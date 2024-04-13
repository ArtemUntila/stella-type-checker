package artem.untila.typechecker

import artem.untila.typechecker.error.TypeCheckError
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicContainer.dynamicContainer
import org.junit.jupiter.api.DynamicTest.dynamicTest
import java.io.File

class StellaTypecheckerTest {

    private companion object {
        const val OK_TAG = "OK"
        const val PRINT_SRC = true
        const val PRINT_REPORT = true

        fun testDir(name: String) = File("stella-tests", name)
    }

    @TestFactory
    fun ok(): List<DynamicTest> = testDir("ok").listFiles()!!.map {
        it.test(OK_TAG)
    }

    @TestFactory
    fun bad(): List<DynamicContainer> = testDir("bad").listFiles()!!.map { errorDir ->
        val errorTag = errorDir.name
        dynamicContainer(errorTag, errorDir.listFiles()!!.map { it.test(errorTag) })
    }

    private fun File.test(tag: String): DynamicTest = dynamicTest(nameWithoutExtension) {
        if (PRINT_SRC) println(readText())
        assertEquals(tag, typecheckTag())
    }

    private fun File.typecheckTag(): String {
        return try {
            typecheckFile(this)
            OK_TAG
        } catch (tce: TypeCheckError) {
            if (PRINT_REPORT) tce.report(System.err)
            tce.errorTag
        } catch (e: Exception) {
            e.message ?: "Exception"
        }
    }
}