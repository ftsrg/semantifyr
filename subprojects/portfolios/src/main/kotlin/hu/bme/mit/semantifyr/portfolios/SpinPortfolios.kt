/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import hu.bme.mit.semantifyr.backend.AvailabilityReport
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.VerificationRequest
import hu.bme.mit.semantifyr.backends.spin.verification.SpinBackend
import hu.bme.mit.semantifyr.backends.spin.verification.SpinConfig
import hu.bme.mit.semantifyr.verification.ProgressContext
import hu.bme.mit.semantifyr.verification.portfolio.BackendExecutor
import hu.bme.mit.semantifyr.verification.portfolio.VerificationPortfolio

internal const val SPIN_FAMILY_ID = "spin"

suspend fun BackendExecutor.runSpin(
    config: SpinConfig,
    request: VerificationRequest,
    environment: ExecutionEnvironment,
): BackendVerificationResult {
    return withPermit {
        SpinBackend.verify(config, request, environment)
    }
}

class SpinSinglePortfolio(
    val config: SpinConfig,
) : VerificationPortfolio() {
    override val id: String = "spin-${config.id}"
    override val displayName: String = "Spin ${config.id}"
    override val description: String = "Single Spin configuration: ${config.extraArguments.joinToString(" ").ifBlank { "<defaults>" }}"
    override val familyId: String = SPIN_FAMILY_ID

    override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
        return SpinBackend.probeAvailability(config, environment)
    }

    override suspend fun verify(
        request: VerificationRequest,
        executor: BackendExecutor,
        environment: ExecutionEnvironment,
        progress: ProgressContext,
    ): BackendVerificationResult {
        return executor.runSpin(config, request, environment)
    }
}
