package org.usvm.machine

import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import java.math.BigInteger

data class TvmConcreteData(
    val contractC4: Cell? = null,
    val initialBalance: BigInteger? = null,
    val addressBits: String? = null,
) {
    init {
        check(addressBits?.matches(addressBitsRegex) != false) {
            "Invalid address bits: $addressBits"
        }
    }

    companion object {
        private val addressBitsRegex = "10{10}[10]{256}".toRegex()
    }
}

@OptIn(ExperimentalStdlibApi::class)
fun String.hexToCell(): Cell {
    return BagOfCells(this.hexToByteArray()).roots.single()
}
