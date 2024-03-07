package artem.untila.typechecker.types

import StellaParser.*
import StellaParserBaseVisitor

class StellaTypeResolver : StellaParserBaseVisitor<StellaType>() {

    override fun visitTypeBool(ctx: TypeBoolContext): StellaType {
        return StellaBool
    }

    override fun visitTypeNat(ctx: TypeNatContext): StellaType {
        return StellaNat
    }

    override fun visitTypeFun(ctx: TypeFunContext): StellaType = with(ctx) {
        return StellaFunction(
            resolve(paramTypes.first()),
            resolve(returnType)
        )
    }

    override fun visitTypeParens(ctx: TypeParensContext): StellaType = with(ctx) {
        return resolve(type_)
    }

    override fun visitTypeUnit(ctx: TypeUnitContext): StellaType {
        return StellaUnit
    }

    override fun visitTypeTuple(ctx: TypeTupleContext): StellaType = with(ctx) {
        return StellaTuple(types.map { resolve(it) })
    }

    override fun visitTypeRecord(ctx: TypeRecordContext): StellaType = with(ctx) {
        return StellaRecord(fieldTypes.associate { it.label.text to resolve(it.type_) })
    }

    override fun visitTypeList(ctx: TypeListContext): StellaType = with(ctx) {
        return StellaList(resolve(types.first()))
    }

    private fun resolve(typeContext: StellatypeContext): StellaType = typeContext.accept(this)
}
