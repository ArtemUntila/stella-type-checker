package artem.untila.typechecker

import artem.untila.typechecker.types.StellaType
import java.util.ArrayDeque

class VariableContext : ArrayDeque<ContextVariable>() {

    operator fun get(name: String): ContextVariable? = firstOrNull { name == it.name }

    fun pushAll(variables: Collection<ContextVariable>) = variables.forEach { push(it) }

    fun pushAll(vararg variables: ContextVariable) = pushAll(variables.asList())

    fun pop(n: Int) = repeat(n) { pop() }

    // Sugary sugar
    inline fun <T> with(variables: Collection<ContextVariable>, block: () -> T): T {
        pushAll(variables)
        val res = block.invoke()
        pop(variables.size)
        return res
    }

    inline fun <T> with(vararg variables: ContextVariable, block: () -> T): T {
        return this.with(variables.asList(), block)
    }
}

data class ContextVariable(val name: String, val type: StellaType)
