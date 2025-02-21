package org.usvm.test.resolver

sealed interface TvmTestInput {
    val usedParameters: List<TvmTestValue>

    data class StackInput(
        override val usedParameters: List<TvmTestValue>,
    ) : TvmTestInput

    data class RecvInternalInput(
        val srcAddress: TvmTestSliceValue,
        val msgBody: TvmTestSliceValue,
        val msgValue: TvmTestIntegerValue,
        val bounce: Boolean,
        val bounced: Boolean,
        val ihrDisabled: Boolean,
        val ihrFee: TvmTestIntegerValue,
        val fwdFee: TvmTestIntegerValue,
        val createdLt: TvmTestIntegerValue,
        val createdAt: TvmTestIntegerValue,
    ) : TvmTestInput {
        override val usedParameters: List<TvmTestValue>
            get() = listOf(
                srcAddress,
                msgBody,
                msgValue,
                TvmTestBooleanValue(bounce),
                TvmTestBooleanValue(bounced),
                TvmTestBooleanValue(ihrDisabled),
                ihrFee,
                fwdFee,
                createdLt,
                createdAt
            )
    }
}
