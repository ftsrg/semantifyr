/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression

enum class SoundnessDirection {
    Exact,
    OverApproximating,
    UnderApproximating,
    ;

    fun trusts(verdict: VerificationVerdict, property: Expression): Boolean {
        if (!verdict.isDecisive) {
            return false
        }
        return when (this) {
            Exact -> true
            OverApproximating -> !hasTrace(verdict, property)
            UnderApproximating -> hasTrace(verdict, property)
        }
    }

    fun throughLowering(lowering: SoundnessDirection): SoundnessDirection {
        if (lowering == Exact) {
            return this
        }
        if (this != Exact && this != lowering) {
            error("A $this run cannot take a $lowering transformation of the model")
        }
        return lowering
    }
}

fun hasTrace(verdict: VerificationVerdict, property: Expression): Boolean {
    return verdict.isDecisive && (verdict == VerificationVerdict.Failed) != (property is EF)
}

interface BackendConfig {
    val id: String
    val soundness: SoundnessDirection
}
