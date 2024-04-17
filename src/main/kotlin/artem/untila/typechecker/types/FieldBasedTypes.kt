package artem.untila.typechecker.types

data class StellaRecord(val fields: List<StellaField>) : StellaType {

    val labels = fields.map { it.label }

    operator fun get(label: String): StellaField? = fields.firstOrNull { label == it.label }

    override fun toString() = fields.joinToString(", ", "{ ", " }")
}

data class StellaVariant(val fields: List<StellaField>) : StellaType {

    operator fun get(label: String): StellaField? = fields.firstOrNull { label == it.label }

    override fun toString() = fields.joinToString(", ", "<| ", " |>")
}

data class StellaField(val label: String, val type: StellaType) : StellaType {

    companion object {
        infix fun String.colon(type: StellaType) = StellaField(this, type)
    }

    override fun toString() = "$label : $type"
}