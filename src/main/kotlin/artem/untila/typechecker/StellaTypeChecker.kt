package artem.untila.typechecker

import StellaParser.*
import artem.untila.typechecker.error.*
import artem.untila.typechecker.types.*
import artem.untila.typechecker.types.StellaFunction.Companion.arrow
import org.antlr.v4.runtime.ParserRuleContext
import java.util.ArrayDeque

class StellaTypeChecker : StellaVisitor<StellaType>() {

    private val typeResolver = StellaTypeResolver()

    private val variableContext = VariableContext()

    private val expectedTypes = ArrayDeque<StellaType>()  // for debugging purposes
    private val expectedType: StellaType
        get() = expectedTypes.peek()

    // Language core
    // Program
    override fun visitProgram(ctx: ProgramContext): StellaType = with(ctx) {
        val funVariables = decls.filterIsInstance<DeclFunContext>().map { it.toContextVariable() }
        variableContext.pushAll(funVariables)

        decls.forEach { it.check() }

        if (variableContext["main"] == null) throw MissingMain()
        return StellaType { "Program" }
    }

    // Function declaration
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

    // Boolean expressions
    override fun visitConstTrue(ctx: ConstTrueContext): StellaType = StellaBool
    override fun visitConstFalse(ctx: ConstFalseContext): StellaType = StellaBool

    override fun visitIf(ctx: IfContext): StellaType = with(ctx) {
        condition.checkOrThrow(StellaBool)

        val thenType = thenExpr.checkOrThrow(expectedType)
        elseExpr.checkOrThrow(thenType)

        return thenType
    }

    // Nat expressions
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

    // First-class functions
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
        args.first().checkOrThrow(function.paramType)
        return function.returnType
    }

    // Variables
    override fun visitVar(ctx: VarContext): StellaType = with(ctx) {
        return variableContext[text]?.type ?: throw UndefinedVariable()
    }

    // #unit-type
    override fun visitConstUnit(ctx: ConstUnitContext): StellaType = StellaUnit

    // #pairs and #tuples
    override fun visitTuple(ctx: TupleContext): StellaType = with(ctx) {
        val types = when (val tuple = expectedType) {
            is StellaTuple -> {
                if (tuple.types.size != exprs.size) { throw UnexpectedTupleLength() }
                exprs.mapIndexed { j, it -> it.checkOrThrow(tuple.types[j]) }
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

    // #records
    override fun visitRecord(ctx: RecordContext): StellaType = with(ctx) {
        val binds = bindings.associate { it.name.text to it.rhs }
        val fields = when (val record = expectedType) {
            is StellaRecord -> {
                if (binds.keys.any { !record.fields.contains(it) }) throw UnexpectedRecordFields()
                if (record.fields.keys.any { !binds.contains(it) }) throw MissingRecordFields()
                binds.mapValues { (l, it) -> it.checkOrThrow(record.fields[l]!!) }
            }
            is StellaAny -> binds.mapValues { (_, it) -> it.check(StellaAny) }
            else -> throw UnexpectedRecord()
        }
        return StellaRecord(fields)
    }

    override fun visitDotRecord(ctx: DotRecordContext): StellaType = with(ctx) {
        val record = expr_.check(StellaAny) as? StellaRecord ?: throw NotARecord()
        return record.fields[label.text] ?: throw UnexpectedFieldAccess()
    }

    // #let-bindings
    override fun visitLet(ctx: LetContext): StellaType = with(ctx) {
        val variable = patternBinding.run { ContextVariable(pat.text, rhs.check(StellaAny)) }
        return body.checkOrThrow(expectedType, variable)
    }

    // #type-ascriptions
    override fun visitTypeAsc(ctx: TypeAscContext): StellaType = with(ctx) {
        if (expectedType != type_.resolve()) throw UnexpectedTypeForExpression()
        return expr_.checkOrThrow(expectedType)
    }

    // #fixpoint-combinator
    override fun visitFix(ctx: FixContext): StellaType = with(ctx) {
        expr_.checkOrThrow(expectedType arrow expectedType)
        return expectedType
    }

    // #lists
    // DANGER: idio(ma)tic Kotlin zone!
    override fun visitList(ctx: ListContext): StellaType = with(ctx) {
        val type = when (val list = expectedType) {
            is StellaList -> {
                exprs.forEach { it.checkOrThrow(list.type) }
                list.type
            }
            is StellaAny -> {
                if (exprs.isEmpty()) throw AmbiguousList()
                val listType = exprs.first().check(StellaAny)
                exprs.drop(1).forEach { it.checkOrThrow(listType) }
                listType
            }
            else -> throw UnexpectedList()
        }
        return StellaList(type)
    }

    override fun visitConsList(ctx: ConsListContext): StellaType = with(ctx) {
        when (val list = expectedType) {
            is StellaList -> {
                head.checkOrThrow(list.type)
                tail.checkOrThrow(list)
            }
            is StellaAny -> {
                StellaList(head.check(StellaAny)).also { tail.checkOrThrow(it) }
            }
            else -> throw UnexpectedList()
        }
    }

    override fun visitHead(ctx: HeadContext): StellaType = visitListOp(ctx.list) { it.type }
    override fun visitTail(ctx: TailContext): StellaType = visitListOp(ctx.list) { it }
    override fun visitIsEmpty(ctx: IsEmptyContext): StellaType = visitListOp(ctx.list) { StellaBool }

    private inline fun visitListOp(expr: ExprContext, block: (StellaList) -> StellaType): StellaType {
        val list = expr.check(StellaAny) as? StellaList ?: throw NotAList()
        return block(list)
    }

    // #sum-types
    override fun visitInl(ctx: InlContext): StellaType = visitInjection(ctx.expr_) { it.left }
    override fun visitInr(ctx: InrContext): StellaType = visitInjection(ctx.expr_) { it.right }

    private inline fun visitInjection(expr: ExprContext, block: (StellaSum) -> StellaType): StellaType {
        return when (val sum = expectedType) {
            is StellaSum -> sum.also { expr.checkOrThrow(block(it)) }
            is StellaAny -> throw AmbiguousSumType()
            else -> throw UnexpectedInjection()
        }
    }

    // Utils
    internal fun ParserRuleContext.check(type: StellaType? = null, vararg variables: ContextVariable): StellaType {
        if (type != null) expectedTypes.push(type)
        variableContext.pushAll(*variables)

        val checkedType = accept(this@StellaTypeChecker)

        if (type != null) expectedTypes.pop()
        variableContext.pop(variables.size)

        return checkedType
    }

    internal fun ExprContext.checkOrNull(type: StellaType, vararg variables: ContextVariable): StellaType? {
        // order of type comparison really matters, because StellaAny overrides equals
        return check(type, *variables).takeIf { type == it }
    }

    internal fun ExprContext.checkOrThrow(type: StellaType, vararg variables: ContextVariable): StellaType {
        return checkOrNull(type, *variables) ?: throw UnexpectedTypeForExpression()
    }

    // Sugary sugar
    private fun ParamDeclContext.toContextVariable() = ContextVariable(name.text, paramType.resolve())
    private fun DeclFunContext.toContextVariable() = ContextVariable(name.text, toStellaFunction())
    private fun DeclFunContext.toStellaFunction() = StellaFunction(paramDecl.paramType.resolve(), returnType.resolve())
    private fun StellatypeContext.resolve(): StellaType = accept(typeResolver)
}