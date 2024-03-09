package artem.untila.typechecker.types

import StellaParser.*
import StellaParserBaseVisitor

class StellaTypeResolver : StellaParserBaseVisitor<StellaType>() {

    override fun visitTypeBool(ctx: TypeBoolContext): StellaBool {
        return StellaBool
    }

    override fun visitTypeNat(ctx: TypeNatContext): StellaNat {
        return StellaNat
    }

    override fun visitTypeFun(ctx: TypeFunContext): StellaFunction = with(ctx) {
        return StellaFunction(
            resolve(paramTypes.first()),
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
        return StellaRecord(fieldTypes.map { visitRecordFieldType(it) }.toSet())
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

    private fun resolve(typeContext: StellatypeContext): StellaType = typeContext.accept(this)
}
