package org.usvm.machine.interpreter

import java.math.BigInteger
import org.ton.cell.Cell
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.readField
import org.usvm.machine.TvmContext
import org.usvm.machine.TvmContext.Companion.dictKeyLengthField
import org.usvm.machine.TvmContext.Companion.sliceDataPosField
import org.usvm.machine.TvmContext.Companion.sliceRefPosField
import org.usvm.machine.TvmContext.TvmInt257Sort
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.bigIntValue
import org.usvm.machine.state.DictId
import org.usvm.machine.state.dictContainsKey
import org.usvm.machine.state.dictGetValue
import org.usvm.machine.state.dictKeyEntries
import org.usvm.machine.state.preloadDataBitsFromCellWithoutChecks
import org.usvm.machine.state.readCellRef
import org.usvm.machine.truncateSliceCell
import org.usvm.mkSizeExpr
import org.usvm.sizeSort
import org.usvm.test.resolver.TvmTestBuilderValue
import org.usvm.test.resolver.TvmTestCellValue
import org.usvm.test.resolver.TvmTestDataCellValue
import org.usvm.test.resolver.TvmTestDictCellValue
import org.usvm.test.resolver.TvmTestIntegerValue
import org.usvm.test.resolver.TvmTestReferenceValue
import org.usvm.test.resolver.TvmTestSliceValue
import org.usvm.test.resolver.TvmTestStateResolver
import org.usvm.test.resolver.transformTestCellIntoCell
import org.usvm.test.resolver.transformTestDataCellIntoCell
import org.usvm.test.resolver.transformTestDictCellIntoCell

class TvmPostProcessor(val ctx: TvmContext) {
    fun postProcessState(scope: TvmStepScopeManager): Unit? = with(ctx) {
        val resolver = scope.calcOnState { TvmTestStateResolver(ctx, models.first(), this) }

        val hashConstraint = generateHashConstraint(scope, resolver)
        val cellDepthConstraint = generateCellDepthConstraint(scope, resolver)

        return scope.assert(hashConstraint and cellDepthConstraint)
    }

    private fun generateCellDepthConstraint(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver
    ): UBoolExpr = with(ctx) {
        val addressToDepth = scope.calcOnState { addressToDepth }

        addressToDepth.entries.fold(trueExpr as UBoolExpr) { acc, (ref, depth) ->
            val curConstraint = fixateValueAndDepth(scope, ref, depth, resolver)
                ?: falseExpr
            acc and curConstraint
        }
    }

    private fun generateHashConstraint(
        scope: TvmStepScopeManager,
        resolver: TvmTestStateResolver
    ): UBoolExpr = with(ctx) {
        val addressToHash = scope.calcOnState { addressToHash }

        addressToHash.entries.fold(trueExpr as UBoolExpr) { acc, (ref, hash) ->
            val curConstraint = fixateValueAndHash(scope, ref, hash, resolver)
                ?: falseExpr
            acc and curConstraint
        }
    }

    /**
     * Generate expression that fixates ref's value given by model, and its hash (which is originally a mock).
     * */
    private fun fixateValueAndHash(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
        hash: UExpr<TvmInt257Sort>,
        resolver: TvmTestStateResolver
    ): UBoolExpr? = with(ctx) {
        val value = resolver.resolveRef(ref)
        val fixateValueCond = fixateConcreteValue(scope, ref, value, resolver)
            ?: return@with null
        val concreteHash = calculateConcreteHash(value)
        val hashCond = hash eq concreteHash
        return fixateValueCond and hashCond
    }

    private fun calculateConcreteHash(value: TvmTestReferenceValue): UExpr<TvmInt257Sort> {
        return when (value) {
            is TvmTestDataCellValue -> {
                val cell = transformTestDataCellIntoCell(value)
                calculateHashOfCell(cell)
            }
            is TvmTestDictCellValue -> {
                val cell = transformTestDictCellIntoCell(value)
                calculateHashOfCell(cell)
            }
            is TvmTestBuilderValue -> {
                TODO()
            }
            is TvmTestSliceValue -> {
                val restCell = truncateSliceCell(value)
                calculateConcreteHash(restCell)
            }
        }
    }

    private fun calculateHashOfCell(cell: Cell): UExpr<TvmInt257Sort> {
        val hash = BigInteger(ByteArray(1) { 0 } + cell.hash().toByteArray())
        return ctx.mkBv(hash, ctx.int257sort)
    }

    private fun fixateValueAndDepth(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
        depth: UExpr<TvmInt257Sort>,
        resolver: TvmTestStateResolver
    ): UBoolExpr? = with(ctx) {
        val value = (resolver.resolveRef(ref) as? TvmTestCellValue)
            ?: return@with null
        val fixateValueCond = fixateConcreteValue(scope, ref, value, resolver)
            ?: return@with null
        val concreteDepth = calculateConcreteDepth(value)
        val depthCond = depth eq concreteDepth
        return fixateValueCond and depthCond
    }

    private fun calculateConcreteDepth(value: TvmTestCellValue): UExpr<TvmInt257Sort> = with(ctx) {
        val cell = transformTestCellIntoCell(value)
        return calculateCellDepth(cell).toBv257()
    }

    private fun calculateCellDepth(cell: Cell): Int {
        if (cell.refs.isEmpty()) {
            return 0
        }

        return 1 + cell.refs.maxOf { calculateCellDepth(it) }
    }

    private fun fixateConcreteValue(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
        value: TvmTestReferenceValue,
        resolver: TvmTestStateResolver,
    ): UBoolExpr? =
        when (value) {
            is TvmTestDataCellValue -> {
                fixateConcreteValueForDataCell(scope, ref, value, resolver)
            }
            is TvmTestSliceValue -> {
                fixateConcreteValueForSlice(scope, ref, value, resolver)
            }
            is TvmTestDictCellValue -> {
                fixateConcreteValueForDictCell(scope, ref, value, resolver)
            }
            is TvmTestBuilderValue -> {
                TODO()
            }
        }

    private fun fixateConcreteValueForSlice(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
        value: TvmTestSliceValue,
        resolver: TvmTestStateResolver,
    ): UBoolExpr? = with(ctx) {
        val dataPosSymbolic = scope.calcOnState { memory.readField(ref, sliceDataPosField, sizeSort) }
        val refPosSymbolic = scope.calcOnState { memory.readField(ref, sliceRefPosField, sizeSort) }
        val posGuard = (dataPosSymbolic eq mkSizeExpr(value.dataPos)) and (refPosSymbolic eq mkSizeExpr(value.refPos))
        val cellRef = scope.calcOnState { memory.readField(ref, TvmContext.sliceCellField, addressSort) }
        val fixateGuard = fixateConcreteValueForDataCell(scope, cellRef, value.cell, resolver)
            ?: return@with null
        return posGuard and fixateGuard
    }

    private fun fixateConcreteValueForDataCell(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
        value: TvmTestDataCellValue,
        resolver: TvmTestStateResolver,
    ): UBoolExpr? = with(ctx) {
        val childrenCond = value.refs.foldIndexed(trueExpr as UBoolExpr) { index, acc, child ->
            val childRef = scope.calcOnState { readCellRef(ref, mkSizeExpr(index)) }
            val currentConstraint = fixateConcreteValue(scope, childRef, child, resolver)
                ?: return@with null
            acc and currentConstraint
        }

        val symbolicData = scope.preloadDataBitsFromCellWithoutChecks(ref, zeroSizeExpr, value.data.length)
            ?: return@with null
        val symbolicDataLength = scope.calcOnState {
            memory.readField(ref, TvmContext.cellDataLengthField, sizeSort)
        }
        val symbolicRefNumber = scope.calcOnState {
            memory.readField(ref, TvmContext.cellRefsLengthField, sizeSort)
        }

        val dataCond = if (value.data.isEmpty()) {
            trueExpr
        } else {
            val concreteData = mkBv(BigInteger(value.data, 2), value.data.length.toUInt())
            (symbolicData eq concreteData)
        }

        val curCond = dataCond and (symbolicDataLength eq mkSizeExpr(value.data.length)) and (symbolicRefNumber eq mkSizeExpr(value.refs.size))

        childrenCond and curCond
    }

    private fun fixateConcreteValueForDictCell(
        scope: TvmStepScopeManager,
        ref: UHeapRef,
        value: TvmTestDictCellValue,
        resolver: TvmTestStateResolver,
    ): UBoolExpr? = with(ctx) {
        val keyLength = scope.calcOnState { memory.readField(ref, dictKeyLengthField, int257sort) }
        var result = keyLength eq value.keyLength.toBv257()

        // TODO shouldn't the ref value also be fixed in case ref is ite ?
        //  After assertion dict can contain more entries, as we are not asserting not containing entries
        val model = resolver.model
        val modelRef = model.eval(ref) as UConcreteHeapRef

        val dictId = DictId(value.keyLength)
        val keySort = mkBvSort(value.keyLength.toUInt())
        val entries = scope.calcOnState {
            dictKeyEntries(model, memory, modelRef, dictId, keySort)
        }

        entries.forEach { entry ->
            val key = entry.setElement
            val keyContains = scope.calcOnState {
                dictContainsKey(ref, dictId, key)
            }
            val entryValue = scope.calcOnState {
                dictGetValue(ref, dictId, key)
            }

            val concreteKey = TvmTestIntegerValue(model.eval(key).bigIntValue())
            val concreteValue = value.entries[concreteKey]
            if (concreteValue == null) {
                result = result and keyContains.not()
            } else {
                val valueConstraint = fixateConcreteValue(scope, entryValue, concreteValue, resolver)
                    ?: return@with null
                result = result and keyContains and valueConstraint
            }
        }

        return result
    }
}