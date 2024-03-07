package artem.untila.typechecker

import artem.untila.typechecker.types.StellaType
import java.util.ArrayDeque

class VariableContext : ArrayDeque<ContextVariable>() {

    operator fun get(name: String): ContextVariable? = firstOrNull { name == it.name }

    fun pushAll(elements: Collection<ContextVariable>) = elements.forEach { push(it) }

    fun pushAll(vararg elements: ContextVariable) = pushAll(elements.asList())

    fun pop(n: Int) = repeat(n) { pop() }
}

data class ContextVariable(val name: String, val type: StellaType)
