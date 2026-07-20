/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import hu.bme.mit.semantifyr.backend.execution.BackendExecutor
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.measureTimedValue

abstract class VerificationContext<TArtifacts, TRawVerdict>(
    protected val backendId: String,
    protected val request: BackendVerificationRequest,
) {
    protected val logger by loggerFactory()

    protected abstract val executor: BackendExecutor

    suspend fun execute(): BackendVerificationResult {
        val metadata = VerificationMetadata(
            backendId = backendId,
            startedAt = Clock.System.now(),
        )

        logger.info { "[$backendId] starting verification (output=${request.artifactOutputPath})" }

        return try {
            executor.prepare()
            runVerification(metadata)
        } catch (c: CancellationException) {
            logger.debug { "[$backendId] cancelled" }
            throw c
        } catch (e: BackendUnsupportedException) {
            logger.info { "[$backendId] verification not supported: ${e.message}" }
            BackendVerificationResult(
                verdict = VerificationVerdict.NotSupported,
                metadata = metadata,
                metrics = BackendMetrics(),
                message = e.message ?: "Unsupported by $backendId",
            )
        } catch (e: Exception) {
            logger.warn("[$backendId] verification threw ${e::class.simpleName}", e)
            BackendVerificationResult(
                verdict = VerificationVerdict.Errored,
                metadata = metadata,
                metrics = BackendMetrics(),
                message = e.message ?: e::class.simpleName ?: "unknown error",
            )
        }
    }

    private suspend fun runVerification(metadata: VerificationMetadata): BackendVerificationResult {
        val (artifacts, preparationDuration) = measureTimedValue {
            prepare()
        }
        logger.info { "[$backendId] prepared in $preparationDuration" }

        val (rawVerdict, verificationDuration) = measureTimedValue {
            run(artifacts)
        }
        logger.info { "[$backendId] returned $rawVerdict in $verificationDuration" }

        val verdict = interpret(rawVerdict, artifacts)

        val (witness, witnessDuration) = measureTimedValue {
            buildWitness(rawVerdict, artifacts)
        }
        val backAnnotationDuration = if (witness != null) {
            logger.info { "[$backendId] back-annotated witness in $witnessDuration" }
            witnessDuration
        } else {
            Duration.ZERO
        }

        return BackendVerificationResult(
            verdict = verdict,
            metadata = metadata,
            metrics = BackendMetrics(
                preparationDuration = preparationDuration,
                verificationDuration = verificationDuration,
                backAnnotationDuration = backAnnotationDuration,
            ),
            inlinedWitness = witness,
        )
    }

    protected abstract suspend fun prepare(): TArtifacts

    protected abstract suspend fun run(artifacts: TArtifacts): TRawVerdict?

    protected abstract fun interpret(rawVerdict: TRawVerdict?, artifacts: TArtifacts): VerificationVerdict

    protected open suspend fun buildWitness(rawVerdict: TRawVerdict?, artifacts: TArtifacts): InlinedWitness? {
        return null
    }
}
