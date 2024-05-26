package artem.untila.typechecker.types

import artem.untila.typechecker.context.ContextVariable

open class StellaFunction(val paramTypes: List<StellaType>, val returnType: StellaType) : StellaType {

    val params = paramTypes.size

    companion object {
        infix fun StellaType.arrow(other: StellaType) = StellaFunction(listOf(this), other)
    }

    override fun toString() = "fn (${paramTypes.joinToString(", ")}) -> $returnType"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StellaFunction) return false

        if (paramTypes != other.paramTypes) return false
        if (returnType != other.returnType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = paramTypes.hashCode()
        result = 31 * result + returnType.hashCode()
        return result
    }
}

class StellaTopLevelFunction(
    val paramVariables: List<ContextVariable>,
    returnType: StellaType,
) : StellaFunction(
    paramVariables.map { it.type },
    returnType
)