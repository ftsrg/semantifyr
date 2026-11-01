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
        val witnessed = (verdict == VerificationVerdict.Failed) != (property is EF)
        return when (this) {
            Exact -> true
            OverApproximating -> !witnessed
            UnderApproximating -> witnessed
        }
    }
}

interface BackendConfig {
    val id: String
    val soundness: SoundnessDirection
}
