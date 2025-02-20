package org.ton.test.gen

import org.ton.test.gen.dsl.TsContext
import org.ton.test.gen.dsl.TsTestBlockBuilder
import org.ton.test.gen.dsl.TsTestFileBuilder
import org.ton.test.gen.dsl.models.TsBlockchain
import org.ton.test.gen.dsl.models.TsCell
import org.ton.test.gen.dsl.models.TsTestFile
import org.ton.test.gen.dsl.models.TsVariable
import org.ton.test.gen.dsl.models.blockchainCreate
import org.ton.test.gen.dsl.models.compileContract
import org.ton.test.gen.dsl.models.now
import org.ton.test.gen.dsl.models.openContract
import org.ton.test.gen.dsl.models.parseAddress
import org.ton.test.gen.dsl.models.toInt
import org.ton.test.gen.dsl.models.toTsValue
import org.ton.test.gen.dsl.render.TsRenderer
import org.ton.test.gen.dsl.testFile
import org.ton.test.gen.dsl.wrapper.basic.TsBasicWrapperDescriptor
import org.ton.test.gen.dsl.wrapper.basic.constructor
import org.ton.test.gen.dsl.wrapper.basic.external
import org.ton.test.gen.dsl.wrapper.basic.initializeContract
import org.ton.test.gen.dsl.wrapper.basic.internal
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.ADDRESS_TAG_LENGTH
import org.usvm.machine.TvmContext.Companion.RECEIVE_EXTERNAL_ID
import org.usvm.machine.TvmContext.Companion.RECEIVE_INTERNAL_ID
import org.usvm.machine.TvmContext.Companion.STD_ADDRESS_TAG
import org.usvm.machine.state.TvmUnknownFailure
import org.usvm.machine.truncateSliceCell
import org.usvm.test.resolver.TvmContractSymbolicTestResult
import org.usvm.test.resolver.TvmMethodFailure
import org.usvm.test.resolver.TvmSymbolicTest
import org.usvm.test.resolver.TvmTerminalMethodSymbolicResult
import org.usvm.test.resolver.TvmTestDataCellValue
import org.usvm.test.resolver.TvmTestIntegerValue
import org.usvm.test.resolver.TvmTestSliceValue
import java.math.BigInteger
import java.nio.file.Path
import java.util.Locale
import org.usvm.machine.TvmContext.Companion.stdMsgAddrSize
import kotlin.io.path.nameWithoutExtension

/**
 * @return the generated test file name, or `null` if test states are empty
 */
fun generateTests(
    analysisResult: TvmContractSymbolicTestResult,
    projectPath: Path,
    sourceRelativePath: Path,
    contractType: TsRenderer.ContractType,
    generateRecvExternalTests: Boolean = false,  // TODO: make `true` default (after fixes for recv_external)
): String? {
    val entryTests = analysisResult.testSuites.flatten()
    return generateTests(entryTests, projectPath, sourceRelativePath, contractType, generateRecvExternalTests)
}

fun generateTests(
    tests: List<TvmSymbolicTest>,
    projectPath: Path,
    sourceRelativePath: Path,
    contractType: TsRenderer.ContractType,
    generateRecvExternalTests: Boolean = false,  // TODO: make `true` default (after fixes for recv_external)
): String? {
    val name = extractContractName(sourceRelativePath)

    val ctx = TsContext()
    val test = ctx.constructTests(name, tests, sourceRelativePath.toString(), generateRecvExternalTests)
        ?: return null

    val renderedTests = TsRenderer(ctx, contractType).renderTests(test)

    writeRenderedTest(projectPath, renderedTests)
    return renderedTests.fileName
}

private fun TsContext.constructTests(
    name: String,
    tests: List<TvmSymbolicTest>,
    sourcePath: String,
    generateRecvExternalTests: Boolean,
): TsTestFile? {
    val recvInternalTests = tests.filter { it.methodId == RECEIVE_INTERNAL_ID && it.result is TvmMethodFailure }

    val recvExternalTests = if (generateRecvExternalTests) {
        tests.filter {
            it.methodId == RECEIVE_EXTERNAL_ID && it.result is TvmMethodFailure && it.externalMessageWasAccepted
        }
    } else {
        emptyList()
    }

    if (recvInternalTests.isEmpty() && recvExternalTests.isEmpty()) {
        return null
    }

    return testFile(name) {
        val wrapperDescriptor = TsBasicWrapperDescriptor(name)
        registerWrapper(wrapperDescriptor)

        val code = newVar("code", TsCell)
        val blockchain = newVar("blockchain", TsBlockchain)

        emptyLine()

        beforeAll {
            code assign compileContract(sourcePath)
        }

        emptyLine()

        registerRecvInternalTests(wrapperDescriptor, recvInternalTests, code, blockchain)

        registerRecvExternalTests(wrapperDescriptor, recvExternalTests, code, blockchain)
    }
}

private data class TestCaseContext(
    val testName: String,
    val test: TvmSymbolicTest,
    val code: TsVariable<TsCell>,
    val blockchain: TsVariable<TsBlockchain>,
)

private fun TsTestFileBuilder.registerTestsForMethod(
    testGroupName: String,
    tests: List<TvmSymbolicTest>,
    code: TsVariable<TsCell>,
    blockchain: TsVariable<TsBlockchain>,
    registerTestBlock: TsTestBlockBuilder.(TestCaseContext) -> Unit,
) {
    if (tests.isEmpty()) {
        return
    }

    val testsWithNames = generateTestNames(tests) zip tests

    describe(testGroupName) {
        testsWithNames.forEach { (name, test) ->
            val ctx = TestCaseContext(name, test, code, blockchain)
            registerTestBlock(ctx)
        }
    }
}

private fun TsTestFileBuilder.registerRecvInternalTests(
    wrapperDescriptor: TsBasicWrapperDescriptor,
    tests: List<TvmSymbolicTest>,
    code: TsVariable<TsCell>,
    blockchain: TsVariable<TsBlockchain>,
) {
    registerTestsForMethod(
        "tsa-tests-recv-internal",
        tests,
        code,
        blockchain,
    ) { ctx ->
        val input = resolveReceiveInternalInput(ctx.test)

        it(ctx.testName) {
            ctx.blockchain assign blockchainCreate(input.configHex)
            ctx.blockchain.now() assign ctx.test.time.toTsValue().toInt()

            emptyLine()

            val data = newVar("data", ctx.test.rootInitialData.toTsValue())
            val contractAddr = newVar("contractAddr", parseAddress(input.address))
            val contractBalance = newVar("contractBalance", input.initialBalance.toTsValue())

            emptyLine()

            val contract = newVar(
                "contract",
                ctx.blockchain.openContract(wrapperDescriptor.constructor(contractAddr, ctx.code, data))
            )
            +contract.initializeContract(ctx.blockchain, contractBalance)

            emptyLine()

            val srcAddr = newVar("srcAddr", parseAddress(input.srcAddress))
            val msgBody = newVar("msgBody", input.msgBody.toTsValue())
            val msgCurrency = newVar("msgCurrency", input.msgCurrency.toTsValue())
            val bounce = newVar("bounce", input.bounce.toTsValue())
            val bounced = newVar("bounced", input.bounced.toTsValue())
            val ihrFee = newVar("ihrFee", input.ihrFee.toTsValue())
            val fwdFee = newVar("fwdFee", input.fwdFee.toTsValue())

            emptyLine()

            val sendMessageResult = newVar(
                "sendMessageResult",
                contract.internal(ctx.blockchain, srcAddr, msgBody, msgCurrency, bounce, bounced, ihrFee, fwdFee)
            )
            sendMessageResult.expectToHaveTransaction {
                from = srcAddr
                to = contractAddr
                exitCode = input.exitCode.toTsValue()
            }
        }
    }
}

private fun TsTestFileBuilder.registerRecvExternalTests(
    wrapperDescriptor: TsBasicWrapperDescriptor,
    tests: List<TvmSymbolicTest>,
    code: TsVariable<TsCell>,
    blockchain: TsVariable<TsBlockchain>,
) {
    registerTestsForMethod(
        "tsa-tests-recv-external",
        tests,
        code,
        blockchain,
    ) { ctx ->
        val input = resolveReceiveExternalInput(ctx.test)

        it(ctx.testName) {
            ctx.blockchain assign blockchainCreate(input.configHex)
            ctx.blockchain.now() assign ctx.test.time.toTsValue().toInt()

            emptyLine()

            val data = newVar("data", ctx.test.rootInitialData.toTsValue())
            val contractAddr = newVar("contractAddr", parseAddress(input.address))
            val contractBalance = newVar("contractBalance", input.initialBalance.toTsValue())

            emptyLine()

            val contract = newVar(
                "contract",
                ctx.blockchain.openContract(wrapperDescriptor.constructor(contractAddr, ctx.code, data))
            )
            +contract.initializeContract(ctx.blockchain, contractBalance)

            emptyLine()

            val msgBody = newVar("msgBody", input.msgBody.toTsValue())

            emptyLine()

            val sendMessageResult = newVar(
                "sendMessageResult",
                contract.external(ctx.blockchain, msgBody)
            )
            sendMessageResult.expectToHaveTransaction {
                to = contractAddr
                exitCode = input.exitCode.toTsValue()
            }
        }
    }
}

private fun resolveReceiveInternalInput(test: TvmSymbolicTest): TvmReceiveInternalInput {
    // assume that recv_internal args have specified order:
    // recv_internal(int balance, int msg_value, cell full_msg, slice msg_body)
    val args = test.usedParameters.reversed()
    val msgBody = args.getOrNull(0)
        ?: TvmTestSliceValue()
    val fullMsg = args.getOrNull(1)
        ?: TvmTestDataCellValue()
    val defaultCurrency = BigInteger.valueOf(TvmContext.MIN_MESSAGE_CURRENCY)
    val msgCurrency = args.getOrNull(2)
        ?: TvmTestIntegerValue(defaultCurrency)

    require(msgBody is TvmTestSliceValue) {
        "Unexpected recv_internal arg at index 0: $msgBody"
    }
    require(fullMsg is TvmTestDataCellValue) {
        "Unexpected recv_internal arg at index 1: $fullMsg"
    }
    require(msgCurrency is TvmTestIntegerValue) {
        "Unexpected recv_internal arg at index 2: $msgCurrency"
    }

    val configHex = transformTestConfigIntoHex(test.config)

    // TODO: this is not really correct, because part of msgCurrency is used for paying for gas
    val balance = test.contractBalance
    val initialBalance = TvmTestIntegerValue(balance.value - msgCurrency.value)

    val contractAddress = extractAddress(test.contractAddress.data)
        ?: error("Unexpected incorrect contract address")

    // if the result of parsing is null, then
    // bits haven't been read during the interpretation,
    // so, we can assign any value to them

    var msgBits = fullMsg.data

    // int_msg_info$0 ihr_disabled:Bool bounce:Bool bounced:Bool
    val bounce = msgBits.getOrNull(2) == '1'
    val bounced = msgBits.getOrNull(3) == '1'
    msgBits = msgBits.drop(4)

    // src:MsgAddress
    val srcAddress = extractAddress(msgBits).also {
        msgBits = skipStdAddress(msgBits) ?: ""
    } ?: ("0:" + "0".repeat(64))

    // dest:MsgAddressInt
    msgBits = skipStdAddress(msgBits) ?: ""

    // value:CurrencyCollection
    msgBits = skipGrams(msgBits) ?: ""
    msgBits = msgBits.drop(1)

    // ihr_fee:Grams
    val ihrFee = loadGrams(msgBits).also {
        msgBits = skipGrams(msgBits) ?: ""
    } ?: ""
    // fwd_fee:Grams
    val fwdFee = loadGrams(msgBits) ?: ""

    val result = test.result
    require(result is TvmTerminalMethodSymbolicResult) {
        "Unexpected test result: $result"
    }

    return TvmReceiveInternalInput(
        configHex = configHex,
        msgBody = truncateSliceCell(msgBody),
        fullMsg = fullMsg,
        msgCurrency = msgCurrency,
        initialBalance = initialBalance,
        time = test.time,
        address = contractAddress,
        srcAddress = srcAddress,
        bounce = bounce,
        bounced = bounced,
        exitCode = result.exitCode.toInt(),
        ihrFee = TvmTestIntegerValue(ihrFee.binaryToUnsignedBigInteger()),
        fwdFee = TvmTestIntegerValue(fwdFee.binaryToUnsignedBigInteger()),
    )
}

private data class TvmReceiveInternalInput(
    val configHex: String,
    val msgBody: TvmTestDataCellValue,
    val fullMsg: TvmTestDataCellValue,
    val msgCurrency: TvmTestIntegerValue,
    val initialBalance: TvmTestIntegerValue,
    val time: TvmTestIntegerValue,
    val address: String,
    val srcAddress: String,
    val bounce: Boolean,
    val bounced: Boolean,
    val exitCode: Int,
    val ihrFee: TvmTestIntegerValue,
    val fwdFee: TvmTestIntegerValue
)

private fun resolveReceiveExternalInput(test: TvmSymbolicTest): TvmReceiveExternalInput {
    // TODO: for now, take into account only in_message
    // in theory, up to 4 arguments can be used (same as for receive_internal)
    val args = test.usedParameters.reversed()
    val msgBody = args.getOrNull(0)
        ?: TvmTestSliceValue()

    require(msgBody is TvmTestSliceValue) {
        "Unexpected recv_external arg at index 0: $msgBody"
    }

    val configHex = transformTestConfigIntoHex(test.config)

    // TODO: this is probably balance at the end of execution. But since we don't take into account gas for now, that's the same thing
    val balance = test.contractBalance

    val contractAddress = extractAddress(test.contractAddress.data)
        ?: error("Unexpected incorrect contract address")

    val result = test.result
    require(result is TvmTerminalMethodSymbolicResult) {
        "Unexpected test result: $result"
    }

    return TvmReceiveExternalInput(
        configHex,
        truncateSliceCell(msgBody),
        contractAddress,
        balance,
        test.time,
        result.exitCode.toInt(),
    )
}

private data class TvmReceiveExternalInput(
    val configHex: String,
    val msgBody: TvmTestDataCellValue,
    val address: String,
    val initialBalance: TvmTestIntegerValue,
    val time: TvmTestIntegerValue,
    val exitCode: Int,
)

private fun generateTestNames(tests: List<TvmSymbolicTest>): List<String> {
    val exitsCounter = mutableMapOf<String, Int>()

    return tests.map { test ->
        val exit = (test.result as? TvmMethodFailure)?.failure?.exit
        val exitName = if (exit is TvmUnknownFailure) {
            "${exit.ruleName}-${exit.exitCode}"
        } else {
            exit?.ruleName ?: "successful"
        }
        val testIdx = exitsCounter.getOrDefault(exitName, 0)

        exitsCounter[exitName] = testIdx + 1

        "$exitName-$testIdx"
    }
}

private fun extractContractName(sourceRelativePath: Path): String {
    val fileName = sourceRelativePath.fileName.nameWithoutExtension
    val words = fileName.split('_', '-')
    val capitalizedWords = words.map { word ->
        word.replaceFirstChar { firstChar ->
            if (firstChar.isLowerCase()) {
                firstChar.titlecase(Locale.getDefault())
            } else {
                firstChar.toString()
            }
        }
    }

    return capitalizedWords.joinToString(separator = "").let {
        // Sometimes, the file with sources can start with digits (for example, if this is a temporary file with BoC from Fift)
        // In this case, we have to add something before contract name to avoid TS syntax errors
        if (it.first().isDigit()) {
            "Contract$it"
        } else {
            it
        }
    }
}

private fun loadGrams(bits: String): String? {
    val len = bits.take(4).binaryToUnsignedBigInteger().toInt()

    if (bits.length < 4 + len * 8) {
        return null
    }

    return bits.drop(4).take(len * 8)
}

private fun skipGrams(bits: String): String? {
    val gramsStr = loadGrams(bits)
        ?: return null

    return bits.drop(4 + gramsStr.length)
}

private fun skipStdAddress(bits: String): String? {
    val tag = bits.take(ADDRESS_TAG_LENGTH)

    if (bits.length < stdMsgAddrSize || tag != STD_ADDRESS_TAG) {
        return null
    }

    return bits.drop(stdMsgAddrSize)
}

private fun extractAddress(bits: String): String? {
    // addr_std$10 anycast:(Maybe Anycast) workchain_id:int8 address:bits256
    val stdAddrLength = ADDRESS_TAG_LENGTH + 1 + TvmContext.STD_WORKCHAIN_BITS + TvmContext.ADDRESS_BITS

    // TODO for now assume that the address is addr_std$10
    if (bits.length < stdAddrLength || bits.take(2) != STD_ADDRESS_TAG) {
        return null
    }

    val workchainBin = bits.drop(3).take(TvmContext.STD_WORKCHAIN_BITS)
    val addrBin = bits.drop(11).take(TvmContext.ADDRESS_BITS)

    val workchain = workchainBin.binaryToSignedDecimal()
    val addr = addrBin.binaryToHex()
    val paddedAddr = addr.padStart(TvmContext.ADDRESS_BITS / 4, '0')

    return "$workchain:$paddedAddr"
}
