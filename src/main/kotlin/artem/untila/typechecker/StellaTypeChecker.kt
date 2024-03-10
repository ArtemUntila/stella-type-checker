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
        val expectedReturnType = when (val function = expectedType) {
            is StellaFunction -> function.let {
                if (it.paramType != param.type) {
                    throw UnexpectedTypeForParameter("${it.paramType}", "${param.type}", param.name, src)
                }
                it.returnType
            }
            null -> null
            else -> throw UnexpectedLambda("$function", src)
        }
        val returnType = returnExpr.checkOrThrow(expectedReturnType, param)
        return StellaFunction(param.type, returnType)
    }

    override fun visitApplication(ctx: ApplicationContext): StellaType = with(ctx) {
        `fun`.check().let {
            if (it !is StellaFunction) throw NotAFunction("$it", `fun`.src, src)
            args.first().checkOrThrow(it.paramType)
            it.returnType
        }
    }

    // Variables
    override fun visitVar(ctx: VarContext): StellaType = with(ctx) {
        return variableContext[text]?.type ?: throw UndefinedVariable(src)
    }

    // #unit-type
    override fun visitConstUnit(ctx: ConstUnitContext): StellaType = StellaUnit

    // #pairs and #tuples
    override fun visitTuple(ctx: TupleContext): StellaType = with(ctx) {
        val types = when (val tuple = expectedType) {
            is StellaTuple -> {
                if (tuple.length != exprs.size) {
                    throw UnexpectedTupleLength(tuple.length, "$tuple", exprs.size, src)
                }
                exprs.mapIndexed { j, it -> it.checkOrThrow(tuple.types[j]) }
            }
            null -> exprs.map { it.check() }
            else -> throw UnexpectedTuple("$tuple", src)
        }
        return StellaTuple(types)
    }

    override fun visitDotTuple(ctx: DotTupleContext): StellaType = with(ctx) {
        expr_.check().let {
            if (it !is StellaTuple) throw NotATuple(expr_.src, "$it", src)

            val j = index.text.toInt()
            if (j > it.length) throw TupleIndexOutOfBounds(j, src, it.length, "$it")

            it.types[j - 1]
        }
    }

    // #records
    override fun visitRecord(ctx: RecordContext): StellaType = with(ctx) {
        val binds by lazy {
            val res = mutableMapOf<String, ExprContext>()
            val duplicates = bindings.mapNotNull {
                val label = it.name.text
                res.put(label, it.rhs)?.let { label }
            }
            if (duplicates.isNotEmpty()) throw DuplicateRecordFields(duplicates, src)
            return@lazy res
        }

        val fields = when (val record = expectedType) {
            is StellaRecord -> {
                (binds.keys - record.labels).takeIf { it.isNotEmpty() }?.let {
                    throw UnexpectedRecordFields(it, "$record", src)
                }
                (record.labels - binds.keys).takeIf { it.isNotEmpty() }?.let {
                    throw MissingRecordFields(it, "$record", src)
                }
                record.fields.map { it.label colon binds[it.label]!!.checkOrThrow(it.type) }
            }
            null -> binds.map { (label, expr) -> label colon expr.check() }
            else -> throw UnexpectedRecord("$record", src)
        }
        return StellaRecord(fields)
    }

    override fun visitDotRecord(ctx: DotRecordContext): StellaType = with(ctx) {
        expr_.check().let {
            if (it !is StellaRecord) throw NotARecord("$it", expr_.src, src)
            it[label.text]?.type ?: throw UnexpectedFieldAccess(label.text, "$it", src)
        }
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
        expectedType?.takeIf { it != ascType }?.let {
            throw UnexpectedTypeForExpression("$ascType", "$it", src)
        }
        return expr_.checkOrThrow(ascType)
    }

    // #fixpoint-combinator
    override fun visitFix(ctx: FixContext): StellaType = with(ctx) {
        val function = when (val t = expectedType) {
            null -> expr_.check().let {
                if (it !is StellaFunction) throw NotAFunction("$it", expr_.src, src)
                val expected = it.paramType arrow it.paramType
                if (expected != it) throw UnexpectedTypeForExpression("$expected", "$it", src)
                return@let it
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
            is StellaList -> list.type
            null -> {
                if (exprs.isEmpty()) throw AmbiguousList()
                exprs.removeFirst().check()
            }
            else -> throw UnexpectedList("$list", src)
        }
        exprs.forEach { it.checkOrThrow(type) }
        return StellaList(type)
    }

    override fun visitConsList(ctx: ConsListContext): StellaType = with(ctx) {
        return when (val list = expectedType) {
            is StellaList -> {
                head.checkOrThrow(list.type)
                tail.checkOrThrow(list)
            }
            null -> StellaList(head.check()).also { tail.checkOrThrow(it) }
            else -> throw UnexpectedList("$list", src)
        }
    }

    override fun visitHead(ctx: HeadContext): StellaType = visitListOp(ctx, ctx.list) { it.type }
    override fun visitTail(ctx: TailContext): StellaType = visitListOp(ctx, ctx.list) { it }
    override fun visitIsEmpty(ctx: IsEmptyContext): StellaType = visitListOp(ctx, ctx.list) { StellaBool }

    private inline fun visitListOp(ctx: ExprContext, expr: ExprContext, block: (StellaList) -> StellaType): StellaType {
        return expr.check().let {
            if (it !is StellaList) throw NotAList("$it", expr.src, ctx.src)
            block(it)
        }
    }

    // #sum-types
    override fun visitInl(ctx: InlContext): StellaType = visitInjection(ctx, ctx.expr_) { it.left }
    override fun visitInr(ctx: InrContext): StellaType = visitInjection(ctx, ctx.expr_) { it.right }

    private inline fun visitInjection(ctx: ExprContext, expr: ExprContext, block: (StellaSum) -> StellaType): StellaType {
        return when (val sum = expectedType) {
            is StellaSum -> sum.also { expr.checkOrThrow(block(it)) }
            null -> throw AmbiguousSumType()
            else -> throw UnexpectedInjection("$sum", ctx.src)
        }
    }

    // #variants
    override fun visitVariant(ctx: VariantContext): StellaType = with(ctx) {
        return when (val variant = expectedType) {
            is StellaVariant -> variant.also {
                val field = it[label.text] ?: throw UnexpectedVariantLabel(label.text, "$variant", src)
                rhs.checkOrThrow(field.type)
            }
            null -> throw AmbiguousVariantType()
            else -> throw UnexpectedVariant("$variant", src)
        }
    }

    // Pattern-matching
    override fun visitMatch(ctx: MatchContext): StellaType {
        return StellaPatternMatcher(expectedType, this).visitMatch(ctx)
    }

    // Utils
    internal fun ExprContext.check(expected: StellaType? = null, vararg variables: ContextVariable): StellaType {
        expectedTypes.addFirst(expected)
        val checkedType = variableContext.with(*variables) {
            accept(this@StellaTypeChecker)
        }
        expectedTypes.removeFirst()
        return checkedType
    }

    internal inline fun <reified T : StellaType> ExprContext.checkOrThrow(
        expected: T?,
        vararg variables: ContextVariable
    ): T {
        val actual = check(expected, *variables)
        return actual.takeIf { expected == null || expected == it } as? T ?: run {
            throw UnexpectedTypeForExpression("$expected", "$actual", src)
        }
    }

    // Sugary sugar
    private fun ParamDeclContext.toContextVariable() = ContextVariable(name.text, paramType.resolve())
    private fun DeclFunContext.toContextVariable() = ContextVariable(name.text, toStellaFunction())
    private fun DeclFunContext.toStellaFunction() = StellaFunction(paramDecl.paramType.resolve(), returnType.resolve())
    private fun StellatypeContext.resolve(): StellaType = accept(typeResolver)
}