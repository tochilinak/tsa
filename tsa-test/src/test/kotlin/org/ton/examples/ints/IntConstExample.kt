package org.ton.examples.ints

import org.ton.test.utils.extractResource
import org.ton.test.utils.funcCompileAndAnalyzeAllMethods
import kotlin.test.Test
import kotlin.test.assertTrue

class IntConstExample {
    private val sourcesPath: String = "/ints/int_const_example.fc"

    @Test
    fun testIntConstExamples() {
        val bytecodeResourcePath = extractResource(sourcesPath)

        val symbolicResult = funcCompileAndAnalyzeAllMethods(bytecodeResourcePath)
        assertTrue(symbolicResult.isNotEmpty())
    }
}
