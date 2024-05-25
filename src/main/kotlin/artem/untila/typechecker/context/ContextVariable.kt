package artem.untila.typechecker.context

import artem.untila.typechecker.types.StellaType

data class ContextVariable(
    override val name: String,
    val type: StellaType
) : Contextable