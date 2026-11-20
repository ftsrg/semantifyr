/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import hu.bme.mit.semantifyr.backend.execution.BackendExecutor
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.WitnessFailedException
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import hu.bme.mit.semantifyr.oxsts.lang.utils.SourceLocation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.measureTimedValue

abstract class VerificationContext<TArtifacts, TRawVerdict>(
    protected val backendId: String,
    private val configuredSoundness: SoundnessDirection,
    protected val request: BackendVerificationRequest,
    private val capabilityChecker: BackendCapabilityChecker,
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
            val unsupported = capabilityChecker.check(request.inlinedOxsts, request.transitionCallTraces)
            if (unsupported.isNotEmpty()) {
                return notSupported(metadata, unsupported)
            }
            val soundness = capabilityChecker.soundness(configuredSoundness, request.inlinedOxsts)
            executor.prepare()
            runVerification(metadata, soundness)
        } catch (c: CancellationException) {
            logger.debug { "[$backendId] cancelled" }
            throw c
        } catch (e: WitnessFailedException) {
            logger.warn { "[$backendId] trace not read back: ${e.message}" }
            BackendVerificationResult(
                verdict = VerificationVerdict.Errored,
                metadata = metadata,
                metrics = BackendMetrics(),
                message = "$backendId's trace could not be read back: ${e.message}",
            )
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

    private fun notSupported(
        metadata: VerificationMetadata,
        unsupported: List<UnsupportedConstruct>,
    ): BackendVerificationResult {
        val constructs = unsupported.map {
            describe(it.element, it.description)
        }.distinct().joinToString("; ")
        logger.info { "[$backendId] verification not supported: $constructs" }
        return BackendVerificationResult(
            verdict = VerificationVerdict.NotSupported,
            metadata = metadata,
            message = "$backendId does not support $constructs",
        )
    }

    protected fun describe(element: EObject, description: String): String {
        val source = sourceElementOf(element)
        val location = SourceLocation.prefixFor(source)
        if (source is VariableDeclaration) {
            return "$location$description (${source.name})"
        }
        return "$location$description"
    }

    private fun sourceElementOf(element: EObject): EObject {
        val flatteningInfo = request.flatteningInfo ?: return element
        val variable = element as? VariableDeclaration ?: return element
        flatteningInfo.arraySlots[variable]?.let {
            return it.arrayVariable
        }
        flatteningInfo.recordFields[variable]?.let {
            return it.recordVariable
        }
        val holder = flatteningInfo.variableHolders[variable] ?: return element
        return flatteningInfo.resolveOriginalVariable(holder, variable)
    }

    private suspend fun runVerification(metadata: VerificationMetadata, soundness: SoundnessDirection): BackendVerificationResult {
        val (artifacts, preparationDuration) = measureTimedValue {
            prepare()
        }
        logger.info { "[$backendId] prepared in $preparationDuration" }

        val (rawVerdict, verificationDuration) = measureTimedValue {
            run(artifacts)
        }
        logger.info { "[$backendId] returned $rawVerdict in $verificationDuration" }

        val verdict = interpret(rawVerdict, artifacts)
        val property = request.inlinedOxsts.property.expression
        val isTrusted = soundness.trusts(verdict, property)

        val (witness, witnessDuration) = measureTimedValue {
            buildWitness(rawVerdict, artifacts)
        }
        if (witness == null) {
            if (isTrusted && hasTrace(verdict, property)) {
                throw WitnessFailedException("$backendId gave a $verdict verdict without its trace")
            }
        } else {
            checkTraceVariables(witness)
            logger.info { "[$backendId] back-annotated witness in $witnessDuration" }
        }

        return BackendVerificationResult(
            verdict = verdict,
            metadata = metadata,
            metrics = BackendMetrics(
                preparationDuration = preparationDuration,
                verificationDuration = verificationDuration,
                backAnnotationDuration = if (witness == null) {
                    Duration.ZERO
                } else {
                    witnessDuration
                },
            ),
            inlinedWitness = witness,
            soundness = soundness,
            isTrusted = isTrusted,
        )
    }

    private fun checkTraceVariables(witness: InlinedWitness) {
        val traceVariables = request.inlinedOxsts.variables.filter {
            it in request.transitionCallTraces.tracerVariables
        }
        val argumentVariables = request.inlinedOxsts.variables.filter {
            it in request.transitionCallTraces.argumentVariables
        }
        if (traceVariables.isEmpty() && argumentVariables.isEmpty()) {
            return
        }
        val states = listOf(witness.initializedState) + witness.transitionStates
        for ((index, state) in states.withIndex()) {
            val values = state.values.associate {
                it.variable to it.value
            }
            for (variable in traceVariables) {
                if (values[variable] !is LiteralBoolean) {
                    throw WitnessFailedException("the trace variable ${variable.name} has no value in state $index")
                }
            }
            for (variable in argumentVariables) {
                if (values[variable] == null) {
                    throw WitnessFailedException("the recorded argument ${variable.name} has no value in state $index")
                }
            }
        }
    }

    protected abstract suspend fun prepare(): TArtifacts

    protected abstract suspend fun run(artifacts: TArtifacts): TRawVerdict?

    protected abstract fun interpret(rawVerdict: TRawVerdict?, artifacts: TArtifacts): VerificationVerdict

    protected open suspend fun buildWitness(rawVerdict: TRawVerdict?, artifacts: TArtifacts): InlinedWitness? {
        return null
    }
}
