package artem.untila.typechecker

import StellaParser.*
import StellaParserBaseVisitor

open class StellaVisitor<T> : StellaParserBaseVisitor<T>() {
    // Meh...
    override fun visitSequence(ctx: SequenceContext): T = ctx.expr1.accept(this)
    override fun visitParenthesisedExpr(ctx: ParenthesisedExprContext): T = ctx.expr_.accept(this)
}