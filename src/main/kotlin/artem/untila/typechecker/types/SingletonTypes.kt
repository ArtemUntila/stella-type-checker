package artem.untila.typechecker.types

object StellaNat : StellaType {
    override fun toString() = "Nat"
}

object StellaBool : StellaType {
    override fun toString() = "Bool"
}

object StellaUnit : StellaType {
    override fun toString() = "Unit"
}

object StellaTop : StellaType {
    override fun toString() = "Top"
}

object StellaBot : StellaType {
    override fun toString() = "Bot"
}