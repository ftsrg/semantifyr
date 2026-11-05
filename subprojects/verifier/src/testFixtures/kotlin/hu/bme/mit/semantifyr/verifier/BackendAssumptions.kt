/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions

fun assumeBackendAvailable(key: ExecutorKey<*>) {
    val report = ExecutionEnvironment.Empty.availability(key) as? AvailabilityReport.Unavailable ?: return
    Assumptions.abort<Unit>("${report.reason}, skipping")
}

fun assumePortfolioAvailable(
    portfolio: VerificationPortfolio,
    environment: ExecutionEnvironment = ExecutionEnvironment.Empty,
) {
    val report = portfolio.availability(environment) as? AvailabilityReport.Unavailable ?: return
    Assumptions.abort<Unit>("${report.reason}, skipping")
}

fun assertVerdict(
    qualifiedName: String,
    expectedVerdict: VerificationVerdict,
    result: VerificationResult,
    diagnostics: String = "",
) {
    Assertions.assertEquals(expectedVerdict, result.verdict) {
        "Expected $qualifiedName verdict $expectedVerdict, got ${result.verdict} (${result.message ?: "no message"}).$diagnostics"
    }
}
