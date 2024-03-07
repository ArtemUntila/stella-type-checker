package artem.untila.typechecker

import StellaParser.*
import StellaParserBaseVisitor
import artem.untila.typechecker.error.*
import artem.untila.typechecker.types.*
import artem.untila.typechecker.types.StellaFunction.Companion.arrow
import org.antlr.v4.runtime.ParserRuleContext

class StellaTypeChecker : StellaParserBaseVisitor<StellaType>() {

    private val typeResolver = StellaTypeResolver()

    private lateinit var variableContext: VariableContext

    private val expectedTypes = ArrayDeque<StellaType>()  // for debugging purposes
    private val expectedType: StellaType
        get() = expectedTypes.first()

    // 1. Language core
    // 1a. Program
    override fun visitProgram(ctx: ProgramContext): StellaType = with(ctx) {
        val funVariables = decls.filterIsInstance<DeclFunContext>().map { it.toContextVariable() }
        if (funVariables.isNotEmpty()) {
            variableContext = VariableContext(funVariables.first()).sub(*funVariables.drop(1).toTypedArray())
        }

        decls.forEach { it.check() }

        if (funVariables.none { it.name == "main" }) throw MissingMain()
        return StellaType { "Program" }
    }

    // 1b. Function declaration
    override fun visitDeclFun(ctx: DeclFunContext): StellaType = with(ctx) {
        val variables = mutableListOf(paramDecl.toContextVariable())
        val function = toStellaFunction()

        // #nested-function-declarations
        localDecls.filterIsInstance<DeclFunContext>().forEach {
            it.check(variables = variables.toTypedArray())
            variables.add(it.toContextVariable())
        }

        returnExpr.checkOrThrow(function.returnType, *variables.toTypedArray())

        return function
    }

    // 1c. Boolean expressions
    override fun visitConstTrue(ctx: ConstTrueContext): StellaType = StellaBool
    override fun visitConstFalse(ctx: ConstFalseContext): StellaType = StellaBool

    override fun visitIf(ctx: IfContext): StellaType = with(ctx) {
        condition.checkOrThrow(StellaBool)

        val thenType = thenExpr.checkOrThrow(expectedType)
        elseExpr.checkOrThrow(thenType)

        return thenType
    }

    // 1d. Nat expressions
    // #natural-literals
    override fun visitConstInt(ctx: ConstIntContext): StellaType = StellaNat

    override fun visitSucc(ctx: SuccContext): StellaType = with(ctx) {
        n.checkOrThrow(StellaNat)
        return StellaNat
    }

    override fun visitIsZero(ctx: IsZeroContext): StellaType = with(ctx) {
        n.checkOrThrow(StellaNat)
        return StellaBool
    }

    override fun visitNatRec(ctx: NatRecContext): StellaType = with(ctx) {
        n.checkOrThrow(StellaNat)
        val initialType = initial.checkOrThrow(expectedType)
        step.checkOrThrow(StellaNat arrow (initialType arrow initialType))
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
        val returnType = returnExpr.checkOrThrow(expectedReturnType, param)
        return StellaFunction(param.type, returnType)
    }

    override fun visitApplication(ctx: ApplicationContext): StellaType = with(ctx) {
        val function = `fun`.check(StellaAny) as? StellaFunction ?: throw NotAFunction()
        expr.checkOrThrow(function.paramType)
        return function.returnType
    }

    // 1f. Variables
    override fun visitVar(ctx: VarContext): StellaType = with(ctx) {
        return variableContext[text]?.type ?: throw UndefinedVariable()
    }

    // 2. #unit-type
    override fun visitConstUnit(ctx: ConstUnitContext): StellaType = StellaUnit

    // 3. #pairs and #tuples
    override fun visitTuple(ctx: TupleContext): StellaType = with(ctx) {
        val types = when (val t = expectedType) {
            is StellaTuple -> {
                if (t.types.size != exprs.size) { throw UnexpectedTupleLength() }
                exprs.mapIndexed { j, it -> it.checkOrThrow(t.types[j]) }
            }
            is StellaAny -> exprs.map { it.check(StellaAny) }
            else -> throw UnexpectedTuple()
        }
        return StellaTuple(types)
    }

    override fun visitDotTuple(ctx: DotTupleContext): StellaType = with(ctx) {
        val tuple = expr_.check(StellaAny) as? StellaTuple ?: throw NotATuple()

        val j = index.text.toInt()
        if (j > tuple.types.size) throw TupleIndexOutOfBounds()

        return tuple.types[j - 1]
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

    // Meh...
    override fun visitSequence(ctx: SequenceContext): StellaType = ctx.expr1.check()
    override fun visitParenthesisedExpr(ctx: ParenthesisedExprContext): StellaType = ctx.expr_.check()

    // Utils
    private fun ParserRuleContext.check(type: StellaType? = null, vararg variables: ContextVariable): StellaType {
        if (type != null) expectedTypes.addFirst(type)
        val prevContext = variableContext
        variableContext = variableContext.sub(*variables)

        val checkedType = accept(this@StellaTypeChecker)

        if (type != null) expectedTypes.removeFirst()
        variableContext = prevContext

        return checkedType
    }

    private fun ExprContext.checkOrNull(type: StellaType, vararg variables: ContextVariable): StellaType? {
        // order of type comparison really matters, because StellaAny overrides equals
        return check(type, *variables).takeIf { type == it }
    }

    private fun ExprContext.checkOrThrow(type: StellaType, vararg variables: ContextVariable): StellaType {
        return checkOrNull(type, *variables) ?: throw UnexpectedTypeForExpression()
    }

    // Sugary sugar
    private fun ParamDeclContext.toContextVariable() = ContextVariable(name.text, paramType.resolve())
    private fun DeclFunContext.toContextVariable() = ContextVariable(name.text, toStellaFunction())
    private fun DeclFunContext.toStellaFunction() = StellaFunction(paramDecl.paramType.resolve(), returnType.resolve())
    private fun StellatypeContext.resolve(): StellaType = accept(typeResolver)
}