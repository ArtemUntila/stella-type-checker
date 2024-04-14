package artem.untila.typechecker.error

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval

// Source code formatting
val ParserRuleContext.src: String
    get() = start.inputStream.getText(Interval.of(start.startIndex, stop.stopIndex))

// Error report formatting
private const val tab = "  "

fun formatted(vararg pairs: Pair<String, String>): String {
    return pairs.joinToString("\n") { (f, s) ->"$f\n$tab$s" }
}
