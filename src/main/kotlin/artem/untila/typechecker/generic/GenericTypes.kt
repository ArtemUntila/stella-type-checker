package artem.untila.typechecker.generic

import artem.untila.typechecker.context.Contextable
import artem.untila.typechecker.types.*

class StellaGenericType(override val name: String) : StellaType, Contextable {
    override fun toString(): String = name
}

class GenericTypeContainer<T : StellaType>(
    val generics: List<StellaGenericType>,
    val type: T
) : StellaType {

    private companion object {
        val genericStub = StellaGenericType("∞")
    }

    override fun toString(): String = "forall ${generics.joinToString(", ")} . $type"

    fun substitute(actualTypes: List<StellaType>): StellaType {
        val mapper: (StellaType) -> StellaType = mapper@{
            if (it !is StellaGenericType) return@mapper it
            val i = generics.indexOf(it)
            return@mapper if (i >= 0) actualTypes[i] else it
        }
        return type.substitute(mapper)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GenericTypeContainer<*>) return false

        if (generics.size != other.generics.size) return false

        val stubs = List(generics.size) { genericStub }
        return this.substitute(stubs) == other.substitute(stubs)
    }

    override fun hashCode(): Int {
        var result = generics.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }
}

private fun StellaType.substitute(mapper: (StellaType) -> StellaType): StellaType = when (this) {
    is GenericTypeContainer<*> -> GenericTypeContainer(generics, type.substitute(mapper))
    is StellaFunction -> StellaFunction(paramTypes.map { it.substitute(mapper) }, returnType.substitute(mapper))
    is StellaList -> StellaList(type.substitute(mapper))
    is StellaTuple -> StellaTuple(types.map { it.substitute(mapper) })
    is StellaRecord -> StellaRecord(fields.map { StellaField(it.label, it.type.substitute(mapper)) })
    is StellaVariant -> StellaVariant(fields.map { StellaField(it.label, it.type.substitute(mapper)) })
    is StellaSum -> StellaSum(left.substitute(mapper), right.substitute(mapper))
    else -> mapper(this)
}
