package artem.untila.typechecker.error

import org.antlr.v4.runtime.tree.*

// Source code formatting
val ParseTree.src: String
    get() = Formatter.format(this)

object Formatter : AbstractParseTreeVisitor<String>() {

    private lateinit var sb: StringBuilder

    fun format(tree: ParseTree): String {
        sb = StringBuilder()
        tree.accept(this)
        return sb.toString()
    }

    private val last: Char?
        get() = sb.lastOrNull()

    override fun visitTerminal(node: TerminalNode): String {
        return when (val s = node.text) {
            "as" -> " as "
            "," -> ", "
            ":" -> " : "
            "{" -> if (last != null && last != ' ') " { " else "{ "
            "}" -> " }"
            "=" -> " = "
            "if" -> "if "
            "then" -> " then "
            "else" -> " else "
            "return" -> "return "
            "match" -> "match "
            "<|" -> "<| "
            "|>" -> " |>"
            "|" -> " | "
            "=>" -> " => "
            ":=" -> " := "
            ";" -> "; "
            else -> s
        }.also { sb.append(it) }
    }
}

// Error report formatting
private const val tab = "  "

fun formatted(vararg pairs: Pair<String, String>): String {
    return pairs.joinToString("\n") { (f, s) ->"$f\n$tab$s" }
}
