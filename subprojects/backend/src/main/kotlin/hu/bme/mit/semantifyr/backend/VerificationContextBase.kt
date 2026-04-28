/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.TimeSource.Monotonic.ValueTimeMark
import kotlin.time.TimeSource.Monotonic.markNow

abstract class VerificationContextBase(
    protected val backendId: String,
    protected val request: VerificationRequest,
) {
    private val logger by loggerFactory()

    suspend fun execute(): BackendVerificationResult {
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
        } catch (e: BackendUnsupportedException) {
            logger.info { "[$backendId] verification of '${request.case.qualifiedName}' not supported: ${e.message}" }
            BackendVerificationResult.notSupported(
                metadata = metadata,
                metrics = VerificationMetrics(totalDuration = totalMark.elapsedNow()),
                message = e.message ?: "Unsupported by $backendId",
            )
        } catch (e: Exception) {
            logger.warn("[$backendId] verification of '${request.case.qualifiedName}' threw ${e::class.simpleName}", e)
            BackendVerificationResult.errored(
                metadata = metadata,
                metrics = VerificationMetrics(totalDuration = totalMark.elapsedNow()),
                message = e.message ?: e::class.simpleName ?: "unknown error",
            )
        }
    }

    protected abstract suspend fun runVerification(
        metadata: VerificationRunMetadata,
        totalMark: ValueTimeMark,
    ): BackendVerificationResult

}
