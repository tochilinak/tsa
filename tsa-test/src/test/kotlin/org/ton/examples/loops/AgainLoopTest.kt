package org.ton.examples.loops

import org.ton.test.utils.compareSymbolicAndConcreteResults
import org.ton.test.utils.compileAndAnalyzeFift
import org.ton.test.utils.extractResource
import org.ton.test.utils.runFiftMethod
import org.ton.test.utils.testConcreteOptions
import kotlin.test.Test

class AgainLoopTest {
    private val againLoopsFiftPath: String = "/loops/AgainLoops.fif"

    @Test
    fun testAgainLoops() {
        val fiftResourcePath = extractResource(againLoopsFiftPath)

        val symbolicResult = compileAndAnalyzeFift(fiftResourcePath, tvmOptions = testConcreteOptions)

        val methodIds = (0..3).toSet()
        compareSymbolicAndConcreteResults(methodIds, symbolicResult) { methodId ->
            runFiftMethod(fiftResourcePath, methodId)
        }
    }
}
