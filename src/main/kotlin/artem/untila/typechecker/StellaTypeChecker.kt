package artem.untila.typechecker

import StellaParser.*
import artem.untila.typechecker.error.*
import artem.untila.typechecker.pattern.StellaPatternMatcher
import artem.untila.typechecker.types.*
import artem.untila.typechecker.types.StellaField.Companion.colon
import artem.untila.typechecker.types.StellaFunction.Companion.arrow

class StellaTypeChecker : StellaVisitor<StellaType>() {

    private val typeResolver = StellaTypeResolver()

    private val variableContext = VariableContext()

    private val expectedTypes = ArrayDeque<StellaType?>()  // for debugging purposes
    private val expectedType: StellaType?
        get() = expectedTypes.first()

    // Language core
    // Program
    override fun visitProgram(ctx: ProgramContext): StellaType = with(ctx) {
        val funDecls = decls.filterIsInstance<DeclFunContext>()
        variableContext.pushAll(funDecls.map { it.toContextVariable() })

        funDecls.forEach { visitDeclFun(it) }

        if (variableContext["main"] == null) throw MissingMain()
        return StellaType { "Program" }
    }

    // Function declaration
    override fun visitDeclFun(ctx: DeclFunContext): StellaType = with(ctx) {
        val variables = mutableListOf(paramDecl.toContextVariable())
        val function = toStellaFunction()

        // #nested-function-declarations
        localDecls.filterIsInstance<DeclFunContext>().forEach {
            variableContext.with(variables) {
                visitDeclFun(it)
            }
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
            null -> null
            else -> throw UnexpectedLambda()
        }
        val returnType = returnExpr.checkOrThrow(expectedReturnType, param)
        return StellaFunction(param.type, returnType)
    }

    override fun visitApplication(ctx: ApplicationContext): StellaType = with(ctx) {
        val function = `fun`.checkOrNull<StellaFunction>() ?: throw NotAFunction()
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
            null -> exprs.map { it.check() }
            else -> throw UnexpectedTuple()
        }
        return StellaTuple(types)
    }

    override fun visitDotTuple(ctx: DotTupleContext): StellaType = with(ctx) {
        val tuple = expr_.checkOrNull<StellaTuple>() ?: throw NotATuple()

        val j = index.text.toInt()
        if (j > tuple.types.size) throw TupleIndexOutOfBounds()

        return tuple.types[j - 1]
    }

    // #records
    override fun visitRecord(ctx: RecordContext): StellaType = with(ctx) {
        val binds = bindings.associate { it.name.text to it.rhs }
        val fields = when (val record = expectedType) {
            is StellaRecord -> {
                if (binds.keys.any { record[it] == null }) throw UnexpectedRecordFields()
                if (record.fields.any { !binds.contains(it.label) }) throw MissingRecordFields()
                record.fields.map { it.label colon binds[it.label]!!.checkOrThrow(it.type) }
            }
            null -> binds.map { (label, expr) -> label colon expr.check() }
            else -> throw UnexpectedRecord()
        }
        return StellaRecord(fields)
    }

    override fun visitDotRecord(ctx: DotRecordContext): StellaType = with(ctx) {
        val record = expr_.checkOrNull<StellaRecord>() ?: throw NotARecord()
        return record[label.text]?.type ?: throw UnexpectedFieldAccess()
    }

    // #let-bindings
    override fun visitLet(ctx: LetContext): StellaType = with(ctx) {
        val variable = patternBinding.run { ContextVariable(pat.text, rhs.check()) }
        return body.checkOrThrow(expectedType, variable)
    }

    // #type-ascriptions
    override fun visitTypeAsc(ctx: TypeAscContext): StellaType = with(ctx) {
        val ascType = type_.resolve()
        // Not necessary check, just trying to follow online-interpreter behaviour
        if (expectedType != null && expectedType != ascType) throw UnexpectedTypeForExpression()
        return expr_.checkOrThrow(ascType)
    }

    // #fixpoint-combinator
    override fun visitFix(ctx: FixContext): StellaType = with(ctx) {
        val function = when (val t = expectedType) {
            null -> {
                val func = expr_.checkOrNull<StellaFunction>() ?: throw NotAFunction()
                func.apply {
                    if (paramType::class != returnType::class) throw UnexpectedTypeForExpression()
                }
            }
            // Not necessary check, just trying to follow online-interpreter behaviour
            else -> expr_.checkOrThrow(t arrow t)
        }
        return function.returnType
    }

    // #lists
    // DANGER: idio(ma)tic Kotlin zone!
    override fun visitList(ctx: ListContext): StellaType = with(ctx) {
        val type = when (val list = expectedType) {
            is StellaList -> {
                exprs.forEach { it.checkOrThrow(list.type) }
                list.type
            }
            null -> {
                if (exprs.isEmpty()) throw AmbiguousList()
                val listType = exprs.first().check()
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
            null -> {
                StellaList(head.check()).also { tail.checkOrThrow(it) }
            }
            else -> throw UnexpectedList()
        }
    }

    override fun visitHead(ctx: HeadContext): StellaType = visitListOp(ctx.list) { it.type }
    override fun visitTail(ctx: TailContext): StellaType = visitListOp(ctx.list) { it }
    override fun visitIsEmpty(ctx: IsEmptyContext): StellaType = visitListOp(ctx.list) { StellaBool }

    private inline fun visitListOp(expr: ExprContext, block: (StellaList) -> StellaType): StellaType {
        val list = expr.checkOrNull<StellaList>() ?: throw NotAList()
        return block(list)
    }

    // #sum-types
    override fun visitInl(ctx: InlContext): StellaType = visitInjection(ctx.expr_) { it.left }
    override fun visitInr(ctx: InrContext): StellaType = visitInjection(ctx.expr_) { it.right }

    private inline fun visitInjection(expr: ExprContext, block: (StellaSum) -> StellaType): StellaType {
        return when (val sum = expectedType) {
            is StellaSum -> sum.also { expr.checkOrThrow(block(it)) }
            null -> throw AmbiguousSumType()
            else -> throw UnexpectedInjection()
        }
    }

    // #variants
    override fun visitVariant(ctx: VariantContext): StellaType = with(ctx) {
        when (val variant = expectedType) {
            is StellaVariant -> variant.also {
                val field = it[label.text] ?: throw UnexpectedVariantLabel()
                rhs.checkOrThrow(field.type)
            }
            null -> throw AmbiguousVariantType()
            else -> throw UnexpectedVariant()
        }
    }

    // Pattern-matching
    override fun visitMatch(ctx: MatchContext): StellaType {
        return StellaPatternMatcher(expectedType, this).visitMatch(ctx)
    }

    // Utils
    internal fun ExprContext.check(type: StellaType? = null, vararg variables: ContextVariable): StellaType {
        expectedTypes.addFirst(type)
        val checkedType = variableContext.with(*variables) {
            accept(this@StellaTypeChecker)
        }
        expectedTypes.removeFirst()
        return checkedType
    }

    internal inline fun <reified T : StellaType> ExprContext.checkOrNull(
        type: T? = null,
        vararg variables: ContextVariable
    ): T? {
        return check(type, *variables).takeIf { type == null || type == it } as? T
    }

    internal inline fun <reified T : StellaType> ExprContext.checkOrThrow(
        type: T?,
        vararg variables: ContextVariable
    ): T {
        return checkOrNull(type, *variables) ?: throw UnexpectedTypeForExpression()
    }

    // Sugary sugar
    private fun ParamDeclContext.toContextVariable() = ContextVariable(name.text, paramType.resolve())
    private fun DeclFunContext.toContextVariable() = ContextVariable(name.text, toStellaFunction())
    private fun DeclFunContext.toStellaFunction() = StellaFunction(paramDecl.paramType.resolve(), returnType.resolve())
    private fun StellatypeContext.resolve(): StellaType = accept(typeResolver)
}