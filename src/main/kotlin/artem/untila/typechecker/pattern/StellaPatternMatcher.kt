package artem.untila.typechecker.pattern

import StellaParser.*
import artem.untila.typechecker.*
import artem.untila.typechecker.error.*
import artem.untila.typechecker.types.*

class StellaPatternMatcher(
    private var expectedType: StellaType?,
    private val typeChecker: StellaTypeChecker
) : StellaVisitor<Any>() {

    internal lateinit var matchType: StellaType
    private lateinit var caseVariable: CaseVariable
    private var exhaustivenessChecker: ExhaustivenessChecker? = null

    override fun visitMatch(ctx: MatchContext): StellaType {
        if (ctx.cases.isEmpty()) throw IllegalEmptyMatching(ctx.src)

        matchType = typeChecker.run { ctx.expr().check() }

        val exhChecker = ExhaustivenessChecker.forType(matchType)
        exhaustivenessChecker = exhChecker

        ctx.cases.forEach { visitMatchCase(it) }

        if (!exhChecker.isExhausted && caseVariable.type != matchType) {
            throw NonexhaustiveMatchPatterns(ctx.src, exhChecker.types.map { "$it" })
        }

        return expectedType!!
    }

    override fun visitMatchCase(ctx: MatchCaseContext): StellaType {
        ctx.pattern_.accept(this@StellaPatternMatcher)
        if (!this::caseVariable.isInitialized) throw UnexpectedPatternForType(ctx.pattern_.src, "$matchType")
        return typeChecker.run {
            ctx.expr_.checkOrThrow(expectedType, caseVariable.toContextVariable()).also {
                expectedType = it
            }
        }
    }

    // #sum-types
    override fun visitPatternInl(ctx: PatternInlContext) = visitPattern<StellaSum>(ctx) { inl to left }
    override fun visitPatternInr(ctx: PatternInrContext) = visitPattern<StellaSum>(ctx) { inr to right }

    // #variants
    override fun visitPatternVariant(ctx: PatternVariantContext) = visitPattern<StellaVariant>(ctx) {
        get(ctx.label.text)?.let { field -> field to field.type }
    }

    private inline fun <reified T : StellaType> visitPattern(
        pattern: PatternContext,
        block: T.() -> Pair<StellaType, StellaType>?
    ) {
        when (val t = matchType) {
            is T -> {
                val (patternType, variableType) = block(t) ?: throw UnexpectedPatternForType(pattern.src, "$t")
                super.visitChildren(pattern)
                caseVariable.type = variableType
                exhaustivenessChecker?.accept(pattern, patternType)
            }
            else -> throw UnexpectedPatternForType(pattern.src, "$t")
        }
    }

    override fun visitPatternVar(ctx: PatternVarContext) {
        caseVariable = CaseVariable(ctx.name.text, matchType)
    }

    // Utils
    private class CaseVariable(var name: String, var type: StellaType)

    private fun CaseVariable.toContextVariable() = ContextVariable(name, type)
}