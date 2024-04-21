package artem.untila.typechecker.subtyping

import artem.untila.typechecker.error.*
import artem.untila.typechecker.types.*

fun StellaType.isSubtypeOf(other: StellaType): Boolean = when {
    other is StellaTop || this is StellaBot -> true
    this::class.java != other::class.java   -> false
    other == this                           -> true
    else -> when (this) {
        is StellaList       -> isSubtypeOf(other as StellaList)
        is StellaTuple      -> isSubtypeOf(other as StellaTuple)
        is StellaRecord     -> isSubtypeOf(other as StellaRecord)
        is StellaVariant    -> isSubtypeOf(other as StellaVariant)
        is StellaRef        -> isSubtypeOf(other as StellaRef)
        is StellaFunction   -> isSubtypeOf(other as StellaFunction)
        else                -> false
    }
}

fun StellaList.isSubtypeOf(other: StellaList): Boolean {
    return this.type.isSubtypeOf(other.type)
}

fun StellaTuple.isSubtypeOf(other: StellaTuple): Boolean {
    if (this.length != other.length) {
        throw UnexpectedTupleLength(other.length, "$other", this.length, "$this")
    }
    for (i in 0 until other.length) {
        if (!this.types[i].isSubtypeOf(other.types[i])) return false
    }
    return true
}

fun StellaRecord.isSubtypeOf(other: StellaRecord): Boolean {
    (other.labels - this.labels).takeIf { it.isNotEmpty() }?.let {
        throw MissingRecordFields(it, "$other", "$this")
    }
    return other.fields.all {
        this[it.label]!!.type.isSubtypeOf(it.type)
    }
}

fun StellaVariant.isSubtypeOf(other: StellaVariant): Boolean {
    this.fields.firstOrNull { other[it.label] == null }?.let {
        throw UnexpectedVariantLabel(it.label, "$other", "$this")
    }
    return this.fields.all {
        it.type.isSubtypeOf(other[it.label]!!.type)
    }
}

fun StellaRef.isSubtypeOf(other: StellaRef): Boolean {
    return this.type.isSubtypeOf(other.type)
}

fun StellaFunction.isSubtypeOf(other: StellaFunction): Boolean {
    for (i in 0 until this.params) {
        if (!other.paramTypes[i].isSubtypeOf(this.paramTypes[i])) return false
    }
    return this.returnType.isSubtypeOf(other.returnType)
}