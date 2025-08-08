package org.ton.examples.loops

import org.ton.test.utils.compareSymbolicAndConcreteResults
import org.ton.test.utils.compileAndAnalyzeFift
import org.ton.test.utils.extractResource
import org.ton.test.utils.runFiftMethod
import org.ton.test.utils.testConcreteOptions
import kotlin.test.Test

class WhileLoopTest {
    private val whileLoopsFiftPath: String = "/loops/WhileLoops.fif"

    @Test
    fun testWhileLoops() {
        val fiftResourcePath = extractResource(whileLoopsFiftPath)

        val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)

        val methodIds = (0..5).toSet()
        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }
}
