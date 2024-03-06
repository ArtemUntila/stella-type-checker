package artem.untila.typechecker

import StellaParser.*
import StellaParserBaseVisitor
import artem.untila.typechecker.error.*
import artem.untila.typechecker.types.*
import artem.untila.typechecker.types.StellaFunction.Companion.arrow

class StellaTypeChecker : StellaParserBaseVisitor<StellaType>() {

    private val typeResolver = StellaTypeResolver()
    private lateinit var declaredFunctions: Map<String, StellaFunction>

    // 1. Language core
    // 1a. Program
    override fun visitProgram(ctx: ProgramContext): StellaType = with(ctx) {
        declaredFunctions = decls.filterIsInstance<DeclFunContext>()
            .associateBy { it.name.text }
            .mapValues { (_, it) -> StellaFunction(it.paramDecl.paramType.resolve(), it.returnType.resolve()) }

        decls.forEach { it.accept(this@StellaTypeChecker) }

        if (declaredFunctions["main"] == null) throw MissingMain()
        return StellaType { "Program" }
    }

    // 1b. Function declaration
    override fun visitDeclFun(ctx: DeclFunContext): StellaType = with(ctx) {
        val paramVariable = paramDecl.toContextVariable()
        val function = declaredFunctions[name.text]!!

        returnExpr.checkOrThrow(VariableContext(paramVariable), function.returnType)

        return function
    }

    // 1c. Boolean expressions
    override fun visitConstTrue(ctx: ConstTrueContext): StellaType = StellaBool
    override fun visitConstFalse(ctx: ConstFalseContext): StellaType = StellaBool

    override fun visitIf(ctx: IfContext): StellaType = with(ctx) {
        condition.checkOrThrow(variableContext, StellaBool)

        val thenType = thenExpr.checkOrThrow(variableContext, expectedType)
        elseExpr.checkOrThrow(variableContext, thenType)

        return thenType
    }

    // 1d. Nat expressions
    override fun visitConstInt(ctx: ConstIntContext): StellaType = StellaNat

    override fun visitSucc(ctx: SuccContext): StellaType = with(ctx) {
        n.checkOrThrow(variableContext, StellaNat)
        return StellaNat
    }

    override fun visitIsZero(ctx: IsZeroContext): StellaType = with(ctx) {
        n.checkOrThrow(variableContext, StellaNat)
        return StellaBool
    }

    override fun visitNatRec(ctx: NatRecContext): StellaType = with(ctx) {
        n.checkOrThrow(variableContext, StellaNat)
        val initialType = initial.checkOrThrow(variableContext, expectedType)
        step.checkOrThrow(variableContext, StellaNat arrow (initialType arrow initialType))
        return initialType
    }

    // 1e. First-class functions
    override fun visitAbstraction(ctx: AbstractionContext): StellaType = with(ctx) {
        val param = paramDecl.toContextVariable()
        val expectedReturnType = when (val t = expectedType) {
            is StellaFunction -> {
                if (t.paramType != param.type) throw UnexpectedTypeForParameter()
                t.returnType
            }
            is StellaAny -> StellaAny
            else -> throw UnexpectedLambda()
        }
        val returnType = returnExpr.checkOrThrow(variableContext.sub(param), expectedReturnType)
        return StellaFunction(param.type, returnType)
    }

    override fun visitApplication(ctx: ApplicationContext): StellaType = with(ctx) {
        val function = `fun`.check(variableContext) as? StellaFunction ?: throw NotAFunction()
        expr.checkOrThrow(variableContext, function.paramType)
        return function.returnType
    }

    // 1f. Variables
    override fun visitVar(ctx: VarContext): StellaType = with(ctx) {
        return (variableContext[text]?.type ?: declaredFunctions[text]) ?: throw UndefinedVariable()
    }

    // 2. Unit type
    override fun visitConstUnit(ctx: ConstUnitContext): StellaType = StellaUnit

    // 3. Tuples
    override fun visitTuple(ctx: TupleContext): StellaType = with(ctx) {
        val types = when (val t = expectedType) {
            is StellaTuple -> {
                if (t.types.size != exprs.size) { throw UnexpectedTupleLength() }
                exprs.mapIndexed { j, it -> it.checkOrThrow(variableContext, t.types[j]) }
            }
            is StellaAny -> exprs.map { it.check(variableContext) }
            else -> throw UnexpectedTuple()
        }
        return StellaTuple(types)
    }

    override fun visitDotTuple(ctx: DotTupleContext): StellaType = with(ctx) {
        val exprType = expr_.check(variableContext) as? StellaTuple ?: throw NotATuple()

        val j = index.text.toInt()
        if (j > exprType.types.size) throw TupleIndexOutOfBounds()

        return exprType.types[j - 1]
    }

    // 4. Records
    override fun visitRecord(ctx: RecordContext): StellaType {
        return super.visitRecord(ctx)
    }

    override fun visitDotRecord(ctx: DotRecordContext): StellaType {
        return super.visitDotRecord(ctx)
    }

    // 5. Let bindings
    override fun visitLet(ctx: LetContext): StellaType {
        return super.visitLet(ctx)
    }

    override fun visitSequence(ctx: SequenceContext): StellaType = with(ctx) {
        return expr1.check(variableContext, expectedType)
    }

    // Utils

    private fun ExprContext.check(context: VariableContext, type: StellaType = StellaAny): StellaType {
        variableContext = context
        expectedType = type
        return accept(this@StellaTypeChecker)
    }

    private fun ExprContext.checkOrNull(context: VariableContext, type: StellaType): StellaType? {
        // order of type comparison really matters, because StellaAny overrides equals
        return check(context, type).takeIf { type == it }
    }

    private fun ExprContext.checkOrThrow(context: VariableContext, type: StellaType): StellaType {
        return checkOrNull(context, type) ?: throw UnexpectedTypeForExpression()
    }

    private fun ParamDeclContext.toContextVariable() = ContextVariable(name.text, paramType.resolve())

    private fun StellatypeContext.resolve(): StellaType = accept(typeResolver)
}