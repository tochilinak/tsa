package org.usvm.machine.interpreter

import org.ton.bytecode.TsaArtificialExecuteContInst
import org.ton.bytecode.TsaArtificialImplicitRetInst
import org.ton.bytecode.TsaArtificialInst
import org.ton.bytecode.TsaArtificialJmpToContInst
import org.ton.bytecode.TsaArtificialLoopEntranceInst
import org.ton.bytecode.TvmArtificialInst
import org.usvm.machine.TvmStepScopeManager
import org.usvm.machine.state.consumeDefaultGas
import org.usvm.machine.state.jump
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.nextStmt
import org.usvm.machine.state.returnFromContinuation
import org.usvm.machine.state.switchToContinuation

class TvmArtificialInstInterpreter {
    fun visit(scope: TvmStepScopeManager, stmt: TvmArtificialInst) {
        check(stmt is TsaArtificialInst) {
            "Unexpected artificial instruction: $stmt"
        }
        when (stmt) {
            is TsaArtificialLoopEntranceInst -> {
                scope.consumeDefaultGas(stmt)
                scope.doWithState { newStmt(stmt.nextStmt()) }
            }
            is TsaArtificialImplicitRetInst -> {
                scope.consumeDefaultGas(stmt)

                scope.returnFromContinuation()
            }
            is TsaArtificialJmpToContInst -> {
                scope.consumeDefaultGas(stmt)

                scope.jump(stmt.cont)
            }
            is TsaArtificialExecuteContInst -> {
                scope.consumeDefaultGas(stmt)

                scope.switchToContinuation(stmt, stmt.cont, returnToTheNextStmt = true)
            }
        }
    }
}