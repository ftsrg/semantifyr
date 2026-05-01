/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification

import hu.bme.mit.semantifyr.backend.VerificationMetrics
import hu.bme.mit.semantifyr.backend.VerificationRunMetadata
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.verification.witness.OxstsClassAssumptionWitness
import hu.bme.mit.semantifyr.verification.witness.SerializableCallTraceData
import hu.bme.mit.semantifyr.verification.witness.SerializableWitnessStateData

sealed interface VerificationTrace {
    data object NoTrace : VerificationTrace

    data class OxstsWitness(
        val classWitness: OxstsClassAssumptionWitness,
        val backAnnotatedWitness: InlinedOxsts,
        val witnessState: SerializableWitnessStateData,
        val callTrace: SerializableCallTraceData,
    ) : VerificationTrace
}

data class VerificationResult(
    val verdict: VerificationVerdict,
    val metadata: VerificationRunMetadata,
    val metrics: VerificationMetrics = VerificationMetrics(),
    val verificationTrace: VerificationTrace = VerificationTrace.NoTrace,
    val message: String? = null,
) {
    val isPassed: Boolean get() = verdict == VerificationVerdict.Passed
    val isFailed: Boolean get() = verdict == VerificationVerdict.Failed
    val isDecisive: Boolean get() = verdict.isDecisive

    companion object {
        fun inconclusive(
            metadata: VerificationRunMetadata,
            metrics: VerificationMetrics,
            message: String,
        ): VerificationResult = VerificationResult(
            verdict = VerificationVerdict.Inconclusive,
            metadata = metadata,
            metrics = metrics,
            message = message,
        )

        fun errored(
            metadata: VerificationRunMetadata,
            metrics: VerificationMetrics,
            message: String,
        ): VerificationResult = VerificationResult(
            verdict = VerificationVerdict.Errored,
            metadata = metadata,
            metrics = metrics,
            message = message,
        )
    }
}
