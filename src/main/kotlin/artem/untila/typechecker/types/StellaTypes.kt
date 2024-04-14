package artem.untila.typechecker.types

fun interface StellaType {
    override fun toString(): String
}

object StellaNat : StellaType {
    override fun toString()= "Nat"
}

object StellaBool : StellaType {
    override fun toString() = "Bool"
}

data class StellaFunction(val paramTypes: List<StellaType>, val returnType: StellaType) : StellaType {

    val params = paramTypes.size

    companion object {
        infix fun StellaType.arrow(other: StellaType) = StellaFunction(listOf(this), other)
    }

    override fun toString() = "fn (${paramTypes.joinToString(", ")}) -> $returnType"
}

object StellaUnit : StellaType {
    override fun toString() = "Unit"
}

data class StellaTuple(val types: List<StellaType>) : StellaType {

    val length = types.size

    override fun toString() = types.joinToString(", ", "{", "}")
}

data class StellaRecord(val fields: List<StellaField>) : StellaType {

    val labels = fields.map { it.label }

    operator fun get(label: String): StellaField? = fields.firstOrNull { label == it.label }

    override fun toString() = fields.joinToString(", ", "{ ", " }")
}

data class StellaList(val type: StellaType) : StellaType {
    override fun toString() = "[$type]"
}

data class StellaSum(val left: StellaType, val right: StellaType) : StellaType {

    val inl = StellaInjection("inl", left)
    val inr = StellaInjection("inr", right)

    override fun toString() = "$left + $right"
}

data class StellaVariant(val fields: List<StellaField>) : StellaType {

    operator fun get(label: String): StellaField? = fields.firstOrNull { label == it.label }

    override fun toString() = fields.joinToString(", ", "<| ", " |>")
}

data class StellaRef(val type: StellaType) : StellaType {
    override fun toString() = "&$type"
}

data class StellaField(val label: String, val type: StellaType) : StellaType {

    companion object {
        infix fun String.colon(type: StellaType) = StellaField(this, type)
    }

    override fun toString() = "$label : $type"
}

data class StellaInjection(val name: String, val type: StellaType) : StellaType {
    override fun toString() = "$name($type)"
}