package artem.untila.typechecker

import StellaLexer
import StellaParser
import artem.untila.typechecker.error.TypeCheckError
import org.antlr.v4.runtime.*
import java.io.File
import java.io.InputStream
import kotlin.system.exitProcess

fun main() {
    try {
        typecheckStream(System.`in`)
    } catch (tce: TypeCheckError) {
        tce.report(System.err)
        exitProcess(tce.errorTag.hashCode())
    }
}

fun typecheckStream(stream: InputStream) = typecheck(CharStreams.fromStream(stream))

fun typecheckCode(code: String) = typecheck(CharStreams.fromString(code))

fun typecheckFile(file: File) = typecheck(CharStreams.fromFileName(file.path))

fun typecheck(input: CharStream) {
    val lexer = StellaLexer(input)
    val tokens = CommonTokenStream(lexer)
    val parser = StellaParser(tokens)
    val program = parser.program()
    val typeChecker = StellaTypeChecker()
    program.accept(typeChecker)
}
