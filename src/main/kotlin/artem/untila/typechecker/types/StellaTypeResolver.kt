package artem.untila.typechecker.types

import StellaParser.*
import StellaParserBaseVisitor

object StellaTypeResolver : StellaParserBaseVisitor<StellaType>() {

    override fun visitTypeBool(ctx: TypeBoolContext): StellaBool {
        return StellaBool
    }

    override fun visitTypeNat(ctx: TypeNatContext): StellaNat {
        return StellaNat
    }

    override fun visitTypeFun(ctx: TypeFunContext): StellaFunction = with(ctx) {
        return StellaFunction(
            paramTypes.map { resolve(it) },
            resolve(returnType)
        )
    }

    override fun visitTypeParens(ctx: TypeParensContext): StellaType = with(ctx) {
        return resolve(type_)
    }

    override fun visitTypeUnit(ctx: TypeUnitContext): StellaUnit {
        return StellaUnit
    }

    override fun visitTypeTuple(ctx: TypeTupleContext): StellaTuple = with(ctx) {
        return StellaTuple(types.map { resolve(it) })
    }

    override fun visitTypeRecord(ctx: TypeRecordContext): StellaRecord = with(ctx) {
        return StellaRecord(fieldTypes.map { visitRecordFieldType(it) })
    }

    override fun visitRecordFieldType(ctx: RecordFieldTypeContext): StellaField = with(ctx) {
        return StellaField(label.text, resolve(type_))
    }

    override fun visitTypeList(ctx: TypeListContext): StellaList = with(ctx) {
        return StellaList(resolve(type_))
    }

    override fun visitTypeSum(ctx: TypeSumContext): StellaSum = with(ctx) {
        return StellaSum(resolve(left), resolve(right))
    }

    override fun visitTypeVariant(ctx: TypeVariantContext): StellaVariant = with(ctx) {
        return StellaVariant(fieldTypes.map { visitVariantFieldType(it) })
    }

    override fun visitVariantFieldType(ctx: VariantFieldTypeContext): StellaField = with(ctx) {
        return StellaField(label.text, resolve(type_))
    }

    override fun visitTypeRef(ctx: TypeRefContext): StellaRef = with(ctx) {
        return StellaRef(resolve(type_))
    }

    override fun visitTypeTop(ctx: TypeTopContext): StellaTop {
        return StellaTop
    }

    override fun visitTypeBottom(ctx: TypeBottomContext): StellaBot {
        return StellaBot
    }

    override fun visitTypeAuto(ctx: TypeAutoContext): StellaAuto {
        return StellaAuto
    }

    fun resolve(typeContext: StellatypeContext): StellaType = typeContext.accept(this)
}
