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
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvBackend
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvConfig
import hu.bme.mit.semantifyr.verification.ProgressContext
import hu.bme.mit.semantifyr.verification.portfolio.BackendExecutor
import hu.bme.mit.semantifyr.verification.portfolio.VerificationPortfolio

internal const val NUXMV_FAMILY_ID = "nuxmv"

suspend fun BackendExecutor.runNuxmv(
    config: NuxmvConfig,
    request: VerificationRequest,
    environment: ExecutionEnvironment,
): BackendVerificationResult {
    return withPermit {
        NuxmvBackend.verify(config, request, environment)
    }
}

class NuxmvSinglePortfolio(
    val config: NuxmvConfig,
) : VerificationPortfolio() {
    override val id: String = "nuxmv-${config.id}"
    override val displayName: String = "nuXmv ${config.id}"
    override val description: String = "Single nuXmv configuration: ${config.checkCommand}"
    override val familyId: String = NUXMV_FAMILY_ID

    override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
        return NuxmvBackend.probeAvailability(config, environment)
    }

    override suspend fun verify(
        request: VerificationRequest,
        executor: BackendExecutor,
        environment: ExecutionEnvironment,
        progress: ProgressContext,
    ): BackendVerificationResult {
        return executor.runNuxmv(config, request, environment)
    }
}
