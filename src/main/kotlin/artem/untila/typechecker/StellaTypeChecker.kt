package artem.untila.typechecker

import StellaParser.*
import artem.untila.typechecker.error.*
import artem.untila.typechecker.pattern.StellaPatternMatcher
import artem.untila.typechecker.subtyping.isSubtypeOf
import artem.untila.typechecker.types.*
import artem.untila.typechecker.types.StellaField.Companion.colon
import artem.untila.typechecker.types.StellaFunction.Companion.arrow

class StellaTypeChecker : StellaVisitor<StellaType>() {

    private val extensions = mutableSetOf<String>()
    private val structuralSubtyping: Boolean by lazy { extensions.contains("#structural-subtyping") }

    private val variableContext = VariableContext()

    private val expectedTypes = ArrayDeque<StellaType?>()  // for debugging purposes
    private val expectedType: StellaType?
        get() = expectedTypes.first()

    private val anyTypeIsExpected: Boolean
        get() = expectedType == null || (structuralSubtyping && expectedType == StellaTop)

    // Language core
    // Program
    override fun visitProgram(ctx: ProgramContext): StellaType = with(ctx) {
        extensions.forEach { it.accept(this@StellaTypeChecker) }
        decls.filterIsInstance<DeclExceptionTypeContext>().forEach { visitDeclExceptionType(it) }

        val funDecls = decls.filterIsInstance<DeclFunContext>()
        variableContext.pushAll(funDecls.map { it.toContextVariable() })

        funDecls.forEach { visitDeclFun(it) }

        val main = variableContext["main"] ?: throw MissingMain()
        if ((main.type as StellaFunction).params != 1) throw IncorrectArityOfMain()

        return StellaUnit
    }

    // Extensions
    override fun visitAnExtension(ctx: AnExtensionContext): StellaType = with(ctx) {
        extensionNames.forEach { extensions.add(it.text) }
        return StellaUnit
    }

    // Function declaration
    override fun visitDeclFun(ctx: DeclFunContext): StellaType = with(ctx) {
        val variables = paramDecls.mapTo(mutableListOf()) { it.toContextVariable() }
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
        val params = paramDecls.map { it.toContextVariable() }
        val function = expectedType
        val expectedReturnType = when {
            function is StellaFunction -> {
                if (function.params != params.size) {
                    throw UnexpectedNumberOfParametersInLambda(function.params, "$function", params.size, src)
                }
                if (!structuralSubtyping) {
                    function.paramTypes.forEachIndexed { i, type ->
                        val param = params[i]
                        if (type != param.type) {
                            throw UnexpectedTypeForParameter("$type", "${param.type}", param.name, src)
                        }
                    }
                }
                function.returnType
            }
            anyTypeIsExpected -> null
            else -> throw UnexpectedLambda("$function", src)
        }
        return StellaFunction(
            params.map { it.type },
            returnExpr.checkOrThrow(expectedReturnType, *params.toTypedArray())
        )
    }

    override fun visitApplication(ctx: ApplicationContext): StellaType = with(ctx) {
        `fun`.check().let {
            if (it !is StellaFunction) throw NotAFunction("$it", `fun`.src, src)
            if (it.params != args.size) throw IncorrectNumberOfArguments(it.params, `fun`.src, "$it", args.size, src)
            args.forEachIndexed { i, expr -> expr.checkOrThrow(it.paramTypes[i]) }
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
        val tuple = expectedType
        val types = when {
            tuple is StellaTuple -> {
                if (tuple.length != exprs.size) {
                    throw UnexpectedTupleLength(tuple.length, "$tuple", exprs.size, src)
                }
                exprs.mapIndexed { j, it -> it.checkOrThrow(tuple.types[j]) }
            }
            anyTypeIsExpected -> exprs.map { it.check() }
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

        val record = expectedType
        val fields = when {
            record is StellaRecord -> {
                if (!structuralSubtyping) {
                    (binds.keys - record.labels).takeIf { it.isNotEmpty() }?.let {
                        throw UnexpectedRecordFields(it, "$record", src)
                    }
                }
                (record.labels - binds.keys).takeIf { it.isNotEmpty() }?.let {
                    throw MissingRecordFields(it, "$record", src)
                }
                record.fields.map { it.label colon binds[it.label]!!.checkOrThrow(it.type) }
            }
            anyTypeIsExpected -> binds.map { (label, expr) -> label colon expr.check() }
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
        return expr_.checkOrThrow(ascType)
    }

    // #fixpoint-combinator
    override fun visitFix(ctx: FixContext): StellaType = with(ctx) {
        val type = expectedType ?: expr_.check().let {
            if (it !is StellaFunction) throw NotAFunction("$it", expr_.src, src)
            it.returnType
        }
        return expr_.checkOrThrow(type arrow type).returnType
    }

    // #lists
    // DANGER: idio(ma)tic Kotlin zone!
    override fun visitList(ctx: ListContext): StellaType = with(ctx) {
        val list = expectedType
        val type = when {
            list is StellaList -> list.type
            anyTypeIsExpected -> {
                if (exprs.isEmpty()) throw AmbiguousListType()
                exprs.removeFirst().check()
            }
            else -> throw UnexpectedList("$list", src)
        }
        exprs.forEach { it.checkOrThrow(type) }
        return StellaList(type)
    }

    override fun visitConsList(ctx: ConsListContext): StellaType = with(ctx) {
        val list = expectedType
        return when {
            list is StellaList -> {
                head.checkOrThrow(list.type)
                tail.checkOrThrow(list)
            }
            anyTypeIsExpected -> StellaList(head.check()).also { tail.checkOrThrow(it) }
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
        val sum = expectedType
        return when {
            sum is StellaSum -> sum.also { expr.checkOrThrow(block(it)) }
            anyTypeIsExpected -> throw AmbiguousSumType()
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
            null, StellaTop -> {  // following interpreter behaviour...
                if (structuralSubtyping) StellaVariant(listOf(StellaField(label.text, rhs.check())))
                else throw AmbiguousVariantType()
            }
            else -> throw UnexpectedVariant("$variant", src)
        }
    }

    // Pattern-matching
    override fun visitMatch(ctx: MatchContext): StellaType {
        return StellaPatternMatcher(expectedType, this).visitMatch(ctx)
    }

    // #sequencing
    override fun visitSequence(ctx: SequenceContext): StellaType = with(ctx) {
        expr1.checkOrThrow(StellaUnit)
        return expr2.check()
    }

    // #references
    override fun visitRef(ctx: RefContext): StellaType = with(ctx) {
        val ref = expectedType
        return when {
            ref is StellaRef -> ref.also { expr_.checkOrThrow(ref.type) }
            anyTypeIsExpected -> StellaRef(expr_.check())
            else -> throw UnexpectedReference("$ref", ctx.src)
        }
    }

    override fun visitDeref(ctx: DerefContext): StellaType = with(ctx) {
        val ref = expectedType?.let { expr_.checkOrThrow(StellaRef(it)) } ?: expr_.check()
        if (ref !is StellaRef) throw NotAReference("$ref", expr_.src, ctx.src)
        return ref.type
    }

    override fun visitAssign(ctx: AssignContext): StellaType = with(ctx) {
        val ref = lhs.check()
        if (ref !is StellaRef) throw NotAReference("$ref", lhs.src, ctx.src)
        rhs.checkOrThrow(ref.type)
        return StellaUnit
    }

    override fun visitConstMemory(ctx: ConstMemoryContext): StellaType {
        return when (val type = expectedType) {
            is StellaRef, StellaTop -> type  // following interpreter behaviour...
            null -> throw AmbiguousReferenceType()
            else -> throw UnexpectedMemoryAddress("$type", ctx.src)
        }
    }

    // #panic
    override fun visitPanic(ctx: PanicContext): StellaType {
        return expectedType ?: throw AmbiguousPanicType()
    }

    // #exceptions, #exception-type-declaration
    private var exceptionType: StellaType? = null

    override fun visitDeclExceptionType(ctx: DeclExceptionTypeContext): StellaType {
        exceptionType = ctx.exceptionType.resolve()
        return StellaUnit
    }

    override fun visitThrow(ctx: ThrowContext): StellaType = with(ctx) {
        val excType = exceptionType ?: throw ExceptionTypeNotDeclared()
        return when (val type = expectedType) {
            null -> throw AmbiguousThrowType()
            else -> type.also { expr_.checkOrThrow(excType) }
        }
    }

    override fun visitTryCatch(ctx: TryCatchContext): StellaType = with(ctx) {
        val type = tryExpr.checkOrThrow(expectedType)
        val excType = exceptionType ?: throw ExceptionTypeNotDeclared()
        // 267: Doubtful but okay
        val matcher = StellaPatternMatcher(expectedType = type, this@StellaTypeChecker).apply {
            matchType = excType
        }
        val matchDelegate = MatchCaseContext(null, -1).apply {
            pattern_ = pat
            expr_ = fallbackExpr
        }
        matcher.visitMatchCase(matchDelegate)

        return type
    }

    override fun visitTryWith(ctx: TryWithContext): StellaType = with(ctx) {
        val type = tryExpr.checkOrThrow(expectedType)
        return fallbackExpr.checkOrThrow(type)
    }

    // #type-cast
    override fun visitTypeCast(ctx: TypeCastContext): StellaType = with(ctx) {
        expr_.check()
        return type_.resolve()
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
        if (structuralSubtyping && expected != null) {
            if (actual.isSubtypeOf(expected)) return expected
            else throw UnexpectedSubtype("$expected", "$actual", src)
        }
        return actual.takeIf { expected == null || expected == it } as? T ?: run {
            throw UnexpectedTypeForExpression("$expected", "$actual", src)
        }
    }

    // Sugary sugar
    private fun ParamDeclContext.toContextVariable() = ContextVariable(name.text, paramType.resolve())
    private fun DeclFunContext.toContextVariable() = ContextVariable(name.text, toStellaFunction())
    private fun DeclFunContext.toStellaFunction(): StellaFunction {
        return StellaFunction(paramDecls.map { it.paramType.resolve() }, returnType.resolve())
    }

    private fun StellatypeContext.resolve(): StellaType = StellaTypeResolver.resolve(this)
}