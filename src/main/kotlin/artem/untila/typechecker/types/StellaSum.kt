package artem.untila.typechecker.types

data class StellaSum(val left: StellaType, val right: StellaType) : StellaType {

    val inl = StellaInjection("inl", left)
    val inr = StellaInjection("inr", right)

    override fun toString() = "$left + $right"

    data class StellaInjection(val name: String, val type: StellaType) : StellaType {
        override fun toString() = "$name($type)"
    }
}