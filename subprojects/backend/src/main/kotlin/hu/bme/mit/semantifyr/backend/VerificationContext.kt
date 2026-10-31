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
import hu.bme.mit.semantifyr.oxsts.lang.utils.SourceLocation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject
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

    protected open val capabilityChecker: BackendCapabilityChecker = BackendCapabilityChecker.Unlimited

    suspend fun execute(): BackendVerificationResult {
        val metadata = VerificationMetadata(
            backendId = backendId,
            startedAt = Clock.System.now(),
        )

        logger.info { "[$backendId] starting verification (output=${request.artifactOutputPath})" }

        return try {
            val unsupported = capabilityChecker.check(request.inlinedOxsts)
            if (unsupported.isEmpty()) {
                executor.prepare()
                runVerification(metadata)
            } else {
                notSupported(metadata, unsupported)
            }
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
        } catch (e: BackendTimeoutException) {
            logger.info { "[$backendId] verification timed out: ${e.message}" }
            BackendVerificationResult(
                verdict = VerificationVerdict.Timeout,
                metadata = metadata,
                metrics = BackendMetrics(),
                message = e.message,
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

    private fun notSupported(
        metadata: VerificationMetadata,
        unsupported: List<UnsupportedConstruct>,
    ): BackendVerificationResult {
        val constructs = unsupported.joinToString("; ") {
            describe(it)
        }
        logger.info { "[$backendId] verification not supported: $constructs" }
        return BackendVerificationResult(
            verdict = VerificationVerdict.NotSupported,
            metadata = metadata,
            message = "$backendId does not support $constructs",
        )
    }

    private fun describe(construct: UnsupportedConstruct): String {
        val source = sourceElementOf(construct.element)
        val location = SourceLocation.prefixFor(source)
        if (source is VariableDeclaration) {
            return "$location${construct.description} (${source.name})"
        }
        return "$location${construct.description}"
    }

    private fun sourceElementOf(element: EObject): EObject {
        val flatteningInfo = request.flatteningInfo ?: return element
        val variable = element as? VariableDeclaration ?: return element
        val holder = flatteningInfo.variableHolders[variable] ?: return element
        return flatteningInfo.resolveOriginalVariable(holder, variable)
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
