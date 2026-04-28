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
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalBackend
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalConfig
import hu.bme.mit.semantifyr.verification.ProgressContext
import hu.bme.mit.semantifyr.verification.portfolio.BackendExecutor
import hu.bme.mit.semantifyr.verification.portfolio.VerificationPortfolio

internal const val UPPAAL_FAMILY_ID = "uppaal"

suspend fun BackendExecutor.runUppaal(
    config: UppaalConfig,
    request: VerificationRequest,
    environment: ExecutionEnvironment,
): BackendVerificationResult {
    return withPermit {
        UppaalBackend.verify(config, request, environment)
    }
}

class UppaalSinglePortfolio(
    val config: UppaalConfig,
) : VerificationPortfolio() {
    override val id: String = "uppaal-${config.id}"
    override val displayName: String = "Uppaal ${config.id}"
    override val description: String = "Single Uppaal configuration: ${config.parameters.ifBlank { "<defaults>" }}"
    override val familyId: String = UPPAAL_FAMILY_ID

    override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
        return UppaalBackend.probeAvailability(config, environment)
    }

    override suspend fun verify(
        request: VerificationRequest,
        executor: BackendExecutor,
        environment: ExecutionEnvironment,
        progress: ProgressContext,
    ): BackendVerificationResult {
        return executor.runUppaal(config, request, environment)
    }
}
