/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification

import hu.bme.mit.semantifyr.backend.AvailabilityReport
import hu.bme.mit.semantifyr.backend.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.VerificationBackend
import hu.bme.mit.semantifyr.backend.VerificationRequest
import hu.bme.mit.semantifyr.backend.VerificationResult
import hu.bme.mit.semantifyr.verification.portfolio.BackendExecutor
import hu.bme.mit.semantifyr.verification.portfolio.VerificationPortfolio

/**
 * Test-only portfolio that runs a single backend with a single config. Backend tests use this so
 * they do not need to depend on `:portfolios` just to drive their own backend; and so a backend's
 * verificationTest can exercise every config the backend ships, independently of which configs the
 * central registry happens to expose.
 */
class SingleBackendPortfolio<T : Any>(
    private val backend: VerificationBackend<T>,
    private val config: T,
    private val configId: String,
) : VerificationPortfolio() {

    override val id: String = "${backend.id}-$configId"
    override val displayName: String = "${backend.id} $configId (test)"
    override val description: String = "Test portfolio running ${backend.id} with config $configId."
    override val familyId: String = backend.id

    override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
        return backend.probeAvailability(config, environment)
    }

    override suspend fun verify(
        request: VerificationRequest,
        executor: BackendExecutor,
        environment: ExecutionEnvironment,
        progress: ProgressContext,
    ): VerificationResult {
        return executor.withPermit {
            backend.verify(config, request, environment)
        }
    }
}
