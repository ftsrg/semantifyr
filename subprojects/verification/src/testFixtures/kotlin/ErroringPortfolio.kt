/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.verification.portfolio.ConcurrencyGate
import hu.bme.mit.semantifyr.verification.portfolio.VerificationPortfolio
import java.util.concurrent.atomic.AtomicInteger

class ErroringPortfolio(
    override val id: String = "erroring",
) : VerificationPortfolio() {
    override val displayName: String = id
    override val description: String = "test-only"

    val invocations = AtomicInteger(0)

    override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
        return AvailabilityReport.Available
    }

    override suspend fun verify(
        parentInjector: Injector,
        request: BackendVerificationRequest,
        gate: ConcurrencyGate,
        environment: ExecutionEnvironment,
        progress: ProgressContext,
    ): BackendVerificationResult {
        invocations.incrementAndGet()
        error("ErroringPortfolio.verify was called but should not have been")
    }
}
