package org.ton.bytecode

import kotlinx.serialization.Serializable

sealed interface TsaArtificialInst : TvmArtificialInst

/**
 * Instruction that marks the beginning of a loop iteration
 */
@Serializable
data class TsaArtificialLoopEntranceInst(
    val id: UInt,
    override val location: TvmInstLocation,
) : TsaArtificialInst {
    override val mnemonic: String get() = "artificial_loop_entrance"

    init {
        checkLocationInitialized()
    }
}

@Serializable
data class TsaArtificialImplicitRetInst(
    override val location: TvmInstLocation,
) : TsaArtificialInst {
    override val mnemonic: String get() = "implicit RET"
    override val gasConsumption get() = TvmFixedGas(value = 5)

    init {
        checkLocationInitialized()
    }
}

sealed interface TsaArtificialContInst : TsaArtificialInst {
    val cont: TvmContinuation
}

@Serializable
data class TsaArtificialJmpToContInst(
    override val cont: TvmContinuation,
    override val location: TvmInstLocation,
) : TsaArtificialContInst {
    override val mnemonic: String get() = "artificial_jmp_to_$cont"

    init {
        checkLocationInitialized()
    }
}

class TsaArtificialExecuteContInst(
    override val cont: TvmContinuation,
    override val location: TvmInstLocation
) : TsaArtificialContInst {
    override val mnemonic: String get() = "artificial_execute_$cont"

    init {
        checkLocationInitialized()
    }
}
