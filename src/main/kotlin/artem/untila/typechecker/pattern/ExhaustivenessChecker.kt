package artem.untila.typechecker.pattern

import StellaParser.*
import artem.untila.typechecker.types.*

sealed interface ExhaustivenessChecker {

    companion object {
        fun forType(type: StellaType): ExhaustivenessChecker = when (type) {
            is StellaSum -> ExhaustivenessCheckerSum(type)
            is StellaVariant -> ExhaustivenessCheckerVariant(type)
            else -> ExhaustivenessCheckerBase(type)
        }
    }

    val types: Collection<StellaType>
    val isExhausted: Boolean

    fun accept(pattern: PatternContext, type: StellaType)

}

open class ExhaustivenessCheckerBase(private val matchType: StellaType) : ExhaustivenessChecker {

    open val patterns: MutableCollection<Class<out PatternContext>> = mutableListOf()
    override val types: MutableCollection<StellaType> = mutableListOf()

    private var containsSuperPattern = false

    override val isExhausted: Boolean
        get() = containsSuperPattern || (patterns.isEmpty() && types.isEmpty())

    override fun accept(pattern: PatternContext, type: StellaType) {
        if (pattern is PatternVarContext && matchType == type) containsSuperPattern = true
        patterns.remove(pattern::class.java)
        types.remove(type)
    }
}

class ExhaustivenessCheckerSum(sum: StellaSum) : ExhaustivenessCheckerBase(sum) {
    override val patterns: MutableCollection<Class<out PatternContext>> = mutableListOf(
        PatternInlContext::class.java, PatternInrContext::class.java
    )
    override val types: MutableCollection<StellaType> = mutableListOf(sum.inl, sum.inr)
}

class ExhaustivenessCheckerVariant(variant: StellaVariant) : ExhaustivenessCheckerBase(variant) {
    override val patterns: MutableCollection<Class<out PatternContext>> = mutableListOf(
        PatternVariantContext::class.java
    )
    override val types: MutableCollection<StellaType> = variant.fields.toMutableSet()
}