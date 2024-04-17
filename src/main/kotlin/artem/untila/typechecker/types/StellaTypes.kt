package artem.untila.typechecker.types

data class StellaFunction(val paramTypes: List<StellaType>, val returnType: StellaType) : StellaType {

    val params = paramTypes.size

    companion object {
        infix fun StellaType.arrow(other: StellaType) = StellaFunction(listOf(this), other)
    }

    override fun toString() = "fn (${paramTypes.joinToString(", ")}) -> $returnType"
}

data class StellaTuple(val types: List<StellaType>) : StellaType {

    val length = types.size

    override fun toString() = types.joinToString(", ", "{", "}")
}

data class StellaList(val type: StellaType) : StellaType {
    override fun toString() = "[$type]"
}

data class StellaRef(val type: StellaType) : StellaType {
    override fun toString() = "&$type"
}