package artem.untila.typechecker.types

fun interface StellaType {
    override fun toString(): String
}

object StellaAny : StellaType {
    override fun toString() = "Any"

    override fun equals(other: Any?): Boolean {
        return other is StellaType
    }
}

object StellaNat : StellaType {
    override fun toString()= "Nat"
}

object StellaBool : StellaType {
    override fun toString() = "Bool"
}

data class StellaFunction(val paramType: StellaType, val returnType: StellaType) : StellaType {

    companion object {
        infix fun StellaType.arrow(other: StellaType) = StellaFunction(this, other)
    }

    override fun toString() = "($paramType->$returnType)"
}

object StellaUnit : StellaType {
    override fun toString() = "unit"
}

data class StellaTuple(val types: List<StellaType>) : StellaType {

    constructor(vararg types: StellaType) : this(types.toList())

    override fun toString() = types.joinToString(",", "{", "}")
}
