/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.TimeSource.Monotonic.markNow

/**
 * Shared scaffolding for the `execute()` flow of a single verification backend invocation. Holds
 * the `try { runVerification } catch { -> Errored | rethrow CancellationException }` template,
 * metadata construction, and consistent info/warn logging that every backend duplicated.
 *
 * Subclasses implement [runVerification], which receives the already-built [VerificationRunMetadata]
 * and the start [markNow] anchor so it can compute durations however it needs.
 */
abstract class VerificationContextBase(
    /** Backend identifier including config, e.g. `"spin:ic3-invar"`. Used for log prefixes and as the result's backend id. */
    protected val backendId: String,
    protected val request: VerificationRequest,
) {
    private val logger by loggerFactory()

    /**
     * Runs the backend and returns a result. Cancellation is propagated unchanged; any other
     * exception maps to an [VerificationVerdict.Errored] result with the exception message.
     */
    suspend fun execute(): VerificationResult {
        val metadata = VerificationRunMetadata(
            backendId = backendId,
            startedAt = Clock.System.now(),
            caseQualifiedName = request.case.qualifiedName,
        )
        val totalMark = markNow()

        logger.info { "[$backendId] starting verification of '${request.case.qualifiedName}'" }

        return try {
            runVerification(metadata, totalMark)
        } catch (c: CancellationException) {
            logger.debug { "[$backendId] cancelled" }
            throw c
        } catch (e: Exception) {
            logger.warn { "[$backendId] verification of '${request.case.qualifiedName}' threw ${e::class.simpleName}: ${e.message ?: ""}" }
            VerificationResult(
                verdict = VerificationVerdict.Errored,
                metadata = metadata,
                metrics = VerificationMetrics(totalDuration = totalMark.elapsedNow()),
                message = e.message ?: e::class.simpleName,
            )
        }
    }

    protected abstract suspend fun runVerification(
        metadata: VerificationRunMetadata,
        totalMark: kotlin.time.TimeSource.Monotonic.ValueTimeMark,
    ): VerificationResult
}
