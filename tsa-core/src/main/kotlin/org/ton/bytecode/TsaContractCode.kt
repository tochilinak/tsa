package org.ton.bytecode

data class TsaContractCode(
    val mainMethod: TvmInstList,
    val methods: Map<MethodId, TvmMethod>
) {
    var isContractWithTSACheckerFunctions: Boolean = false

    companion object {
        fun construct(tvmContractCode: TvmContractCode): TsaContractCode {
            val newMethods = tvmContractCode.methods.entries.associate { (key, value) ->
                key to value.addReturnStmt()
            }
            return TsaContractCode(mainMethod = tvmContractCode.mainMethod, methods = newMethods)
        }
    }
}

