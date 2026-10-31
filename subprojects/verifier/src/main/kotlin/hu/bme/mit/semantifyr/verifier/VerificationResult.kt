/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import hu.bme.mit.semantifyr.backend.VerificationMetadata
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class VerificationResult(
    val metadata: VerificationMetadata,
    val verdict: VerificationVerdict,
    val metrics: VerificationMetrics,
    @Transient val trace: Trace? = null,
    val message: String? = null,
) {
    val isPassed: Boolean
        get() = verdict == VerificationVerdict.Passed

    val isFailed: Boolean
        get() = verdict == VerificationVerdict.Failed

    val isDecisive: Boolean
        get() = verdict.isDecisive
}
