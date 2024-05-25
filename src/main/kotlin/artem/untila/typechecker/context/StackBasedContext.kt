package artem.untila.typechecker.context

import java.util.ArrayDeque

class StackBasedContext<T : Contextable> : ArrayDeque<T>() {

    operator fun get(name: String): T? = find { name == it.name }

    fun pushAll(variables: Collection<T>) = variables.forEach { push(it) }

    fun pushAll(vararg variables: T) = pushAll(variables.asList())

    fun pop(n: Int) = repeat(n) { pop() }

    inline fun <R> with(elements: Collection<T>, block: () -> R): R {
        pushAll(elements)
        val res = block.invoke()
        pop(elements.size)
        return res
    }

    inline fun <R> with(vararg variables: T, block: () -> R): R {
        return this.with(variables.asList(), block)
    }
}