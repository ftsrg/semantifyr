/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.utils.text.IndentingStringBuilder

class SpinProctypeRenderer @Inject constructor(
    private val spinOperationTransformer: SpinOperationTransformer,
) {

    fun renderInitProctype(builder: IndentingStringBuilder, inlinedOxsts: InlinedOxsts) {
        builder.appendLine("init {")
        builder.indented {
            renderInitBody(this, inlinedOxsts.initTransition.branches)
            renderTranLoop(this, inlinedOxsts.mainTransition.branches)
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

    private fun renderChoiceArm(builder: IndentingStringBuilder, branch: SequenceOperation) {
        builder.appendLine(":: atomic {")
        builder.indented {
            renderAtomicBody(this, branch)
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

    private fun renderAtomicBody(builder: IndentingStringBuilder, branch: SequenceOperation) {
        builder.appendLine("$SPIN_STABLE_FLAG = false;")
        if (branch.steps.isEmpty()) {
            builder.appendLine("skip;")
        } else {
            spinOperationTransformer.transform(branch, builder)
        }
        builder.appendLine("$SPIN_STABLE_FLAG = true;")
    }

    private fun renderTranLoop(builder: IndentingStringBuilder, branches: List<SequenceOperation>) {
        if (branches.isEmpty()) {
            return
        }
        builder.appendLine("do")
        for (branch in branches) {
            renderChoiceArm(builder, branch)
        }
        builder.appendLine("od;")
    }
}
