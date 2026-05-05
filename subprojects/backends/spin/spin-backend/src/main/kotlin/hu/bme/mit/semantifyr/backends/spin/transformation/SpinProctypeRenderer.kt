/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.text.IndentingBuilder
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation

class SpinProctypeRenderer @Inject constructor(
    private val spinOperationTransformer: SpinOperationTransformer,
) {

    fun renderInitProctype(builder: IndentingBuilder, inlinedOxsts: InlinedOxsts) {
        builder.line("init {")
        builder.indented {
            renderInitBody(this, inlinedOxsts.initTransition.branches)
            renderTranLoop(this, inlinedOxsts.mainTransition.branches)
        }
        builder.line("}")
    }

    private fun renderInitBody(builder: IndentingBuilder, branches: List<SequenceOperation>) {
        when {
            branches.isEmpty() -> renderEmptyInit(builder)
            branches.size == 1 -> renderAtomicBranch(builder, branches.single())
            else -> renderInitChoice(builder, branches)
        }
    }

    private fun renderEmptyInit(builder: IndentingBuilder) {
        builder.line("atomic { $SPIN_STABLE_FLAG = true; }")
    }

    private fun renderInitChoice(builder: IndentingBuilder, branches: List<SequenceOperation>) {
        builder.line("if")
        for (branch in branches) {
            renderChoiceArm(builder, branch)
        }
        builder.line("fi;")
    }

    private fun renderChoiceArm(builder: IndentingBuilder, branch: SequenceOperation) {
        builder.line(":: atomic {")
        builder.indented {
            renderAtomicBody(this, branch)
        }
        builder.line("}")
    }

    private fun renderAtomicBranch(builder: IndentingBuilder, branch: SequenceOperation) {
        builder.line("atomic {")
        builder.indented {
            renderAtomicBody(this, branch)
        }
        builder.line("}")
    }

    private fun renderAtomicBody(builder: IndentingBuilder, branch: SequenceOperation) {
        builder.line("$SPIN_STABLE_FLAG = false;")
        if (branch.steps.isEmpty()) {
            builder.line("skip;")
        } else {
            spinOperationTransformer.transform(branch, builder)
        }
        builder.line("$SPIN_STABLE_FLAG = true;")
    }

    private fun renderTranLoop(builder: IndentingBuilder, branches: List<SequenceOperation>) {
        if (branches.isEmpty()) {
            return
        }
        builder.line("do")
        for (branch in branches) {
            renderChoiceArm(builder, branch)
        }
        builder.line("od;")
    }
}
