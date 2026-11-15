/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.transformation.WHOLE_DELAY_VARIABLE_NAME
import hu.bme.mit.semantifyr.backend.transformation.WholeDelay
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.utils.text.IndentingStringBuilder

class SpinProctypeRenderer @Inject constructor(
    private val spinOperationTransformer: SpinOperationTransformer,
    private val spinVariableTransformer: SpinVariableTransformer,
) {

    fun renderInitProctype(
        builder: IndentingStringBuilder,
        inlinedOxsts: InlinedOxsts,
        wholeDelay: WholeDelay?,
    ) {
        builder.appendLine("init {")
        builder.indented {
            renderInitBody(this, inlinedOxsts.initTransition.branches)
            renderTranLoop(this, inlinedOxsts.mainTransition.branches, wholeDelay)
        }
        builder.appendLine("}")
    }

    private fun renderInitBody(builder: IndentingStringBuilder, branches: List<SequenceOperation>) {
        when {
            branches.isEmpty() -> renderEmptyInit(builder)
            branches.size == 1 -> renderAtomicBranch(builder, branches.single())
            else -> renderInitChoice(builder, branches)
        }
    }

    private fun renderEmptyInit(builder: IndentingStringBuilder) {
        builder.appendLine("atomic { $SPIN_STABLE_FLAG = true; }")
    }

    private fun renderInitChoice(builder: IndentingStringBuilder, branches: List<SequenceOperation>) {
        builder.appendLine("if")
        for (branch in branches) {
            renderChoiceArm(builder, branch)
        }
        builder.appendLine("fi;")
    }

    private fun renderChoiceArm(
        builder: IndentingStringBuilder,
        branch: SequenceOperation,
        wholeDelay: WholeDelay? = null,
    ) {
        builder.appendLine(":: atomic {")
        builder.indented {
            renderAtomicBody(this, branch, wholeDelay)
        }
        builder.appendLine("}")
    }

    private fun renderAtomicBranch(builder: IndentingStringBuilder, branch: SequenceOperation) {
        builder.appendLine("atomic {")
        builder.indented {
            renderAtomicBody(this, branch)
        }
        builder.appendLine("}")
    }

    private fun renderAtomicBody(
        builder: IndentingStringBuilder,
        branch: SequenceOperation,
        wholeDelay: WholeDelay? = null,
    ) {
        builder.appendLine("$SPIN_STABLE_FLAG = false;")
        if (wholeDelay != null) {
            renderWait(builder, wholeDelay)
        }
        if (branch.steps.isEmpty()) {
            builder.appendLine("skip;")
        } else {
            spinOperationTransformer.transform(branch, builder)
        }
        builder.appendLine("$SPIN_STABLE_FLAG = true;")
    }

    private fun renderWait(builder: IndentingStringBuilder, wholeDelay: WholeDelay) {
        builder.appendLine("select ($WHOLE_DELAY_VARIABLE_NAME : 0 .. ${wholeDelay.longestDelay});")
        for (clock in wholeDelay.clocks) {
            val name = spinVariableTransformer.nameOf(clock.variable)
            val advanced = "$name + $WHOLE_DELAY_VARIABLE_NAME"
            builder.appendLine("$name = ($advanced >= ${clock.saturation} -> ${clock.saturation} : $advanced);")
        }
    }

    private fun renderTranLoop(
        builder: IndentingStringBuilder,
        branches: List<SequenceOperation>,
        wholeDelay: WholeDelay?,
    ) {
        if (branches.isEmpty()) {
            return
        }
        builder.appendLine("do")
        for (branch in branches) {
            renderChoiceArm(builder, branch, wholeDelay)
        }
        builder.appendLine("od;")
    }
}
