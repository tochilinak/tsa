package org.ton.examples.cell

import org.ton.bytecode.MethodId
import org.ton.examples.compareSymbolicAndConcreteResults
import org.ton.examples.compileAndAnalyzeFift
import org.ton.examples.compileFuncToFift
import org.ton.examples.runFiftMethod
import org.ton.examples.testFiftOptions
import org.usvm.machine.toMethodId
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.test.Test

class CellDepthTest {
    private val cellDepthPath: String = "/cell/depth/cell-depth.fc"
    private val unreachableCellDepthPath: String = "/cell/depth/cell-depth-unreachable.fc"

    @Test
    fun cellDepthValueTest() {
        analyzeContract(cellDepthPath)
    }

    @Test
    fun droppedStateTest() {
        analyzeContract(unreachableCellDepthPath)
    }

    private fun analyzeContract(
        contractPath: String,
        methodWhiteList: Set<MethodId> = setOf(0.toMethodId()),
    ) {
        val resourcePath = this::class.java.getResource(contractPath)?.path?.let { Path(it) }
            ?: error("Cannot find resource bytecode $contractPath")
        val tmpFiftFile = kotlin.io.path.createTempFile(suffix = ".boc")

        try {
            compileFuncToFift(resourcePath, tmpFiftFile)

            val symbolicResult = compileAndAnalyzeFift(
                tmpFiftFile,
                methodWhiteList = methodWhiteList,
                tvmOptions = testFiftOptions,
            )

            compareSymbolicAndConcreteResults(methodWhiteList.map { it.toInt() }.toSet(), symbolicResult) { methodId ->
                runFiftMethod(tmpFiftFile, methodId)
            }
        } finally {
            tmpFiftFile.deleteIfExists()
        }
    }
}
