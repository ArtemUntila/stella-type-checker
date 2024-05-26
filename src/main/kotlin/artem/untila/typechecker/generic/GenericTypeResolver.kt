package artem.untila.typechecker.generic

import StellaParser.*
import artem.untila.typechecker.context.StackBasedContext
import artem.untila.typechecker.error.UndefinedTypeVariable
import artem.untila.typechecker.types.*

class GenericTypeResolver : StellaTypeResolver() {

    private val genericsContext = StackBasedContext<StellaGenericType>()

    fun <T> withGenerics(genericTypes: Collection<StellaGenericType>, block: () -> T): T {
        return genericsContext.with(genericTypes, block)
    }

    override fun visitTypeVar(ctx: TypeVarContext): StellaType {
        return ctx.name.text.let { genericsContext[it] ?: throw UndefinedTypeVariable(it) }
    }

    override fun visitTypeForAll(ctx: TypeForAllContext): StellaType {
        val generics = ctx.types.map { it.text }
        return newGenericContainer(generics) {
            resolve(ctx.type_)
        }
    }

    fun <T : StellaType> newGenericContainer(
        generics: List<String>,
        typeProvider: (List<StellaGenericType>) -> T
    ): GenericTypeContainer<T> {
        val genericTypes = generics.distinct().map { StellaGenericType(it) }
        return withGenerics(genericTypes) {
            GenericTypeContainer(
                generics = genericTypes,
                type = typeProvider(genericTypes)
            )
        }
    }
}