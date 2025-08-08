package org.ton.examples

import org.ton.test.utils.analyzeAllMethods
import org.usvm.machine.getResourcePath
import kotlin.test.Test
import kotlin.test.assertTrue

class CounterExample {
    private val bytecodePath: String = "/counter.txt"

    @Test
    fun testCounter() {
        val bytecodeResourcePath = getResourcePath<CounterExample>(bytecodePath)

        val symbolicResult = analyzeAllMethods(bytecodeResourcePath.toString())
        val allTests = symbolicResult.map { it.tests }.flatten()
        val results = allTests.map { it.result }
        assertTrue(results.isNotEmpty())
    }
}
