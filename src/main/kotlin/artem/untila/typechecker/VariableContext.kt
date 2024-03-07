package artem.untila.typechecker

import artem.untila.typechecker.types.StellaType

class VariableContext(
    private val parent: VariableContext?,
    private val variable: ContextVariable
) {
    constructor(variable: ContextVariable) : this(null, variable)

    operator fun get(name: String): ContextVariable? = variable.takeIf { name == it.name } ?: parent?.get(name)

    fun sub(variable: ContextVariable): VariableContext = VariableContext(this, variable)

    fun sub(vararg variables: ContextVariable): VariableContext = variables.fold(this) { ctx, v -> ctx.sub(v) }
}

data class ContextVariable(val name: String, val type: StellaType)
