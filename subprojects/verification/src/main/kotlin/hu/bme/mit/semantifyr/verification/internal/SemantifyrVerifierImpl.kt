/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.internal

import com.google.inject.Injector
import com.google.inject.assistedinject.Assisted
import com.google.inject.assistedinject.AssistedInject
import hu.bme.mit.semantifyr.backend.BackendUnsupportedException
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.VerificationMetadata
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.verification.ProgressContext
import hu.bme.mit.semantifyr.verification.SemantifyrVerifier
import hu.bme.mit.semantifyr.verification.Trace
import hu.bme.mit.semantifyr.verification.VerificationCase
import hu.bme.mit.semantifyr.verification.VerificationMetrics
import hu.bme.mit.semantifyr.verification.VerificationReport
import hu.bme.mit.semantifyr.verification.VerificationResult
import hu.bme.mit.semantifyr.verification.VerifierMetrics
import hu.bme.mit.semantifyr.verification.portfolio.LimitedConcurrencyGate
import hu.bme.mit.semantifyr.verification.portfolio.VerificationPortfolio
import hu.bme.mit.semantifyr.verification.qualifiedNameToDirectoryName
import hu.bme.mit.semantifyr.verification.witness.CallTraceTransformer
import hu.bme.mit.semantifyr.verification.witness.ClassWitnessBackAnnotator
import hu.bme.mit.semantifyr.verification.witness.ClassWitnessTransformer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.createDirectories
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.time.measureTimedValue

private val CANCEL_POLL_INTERVAL = 250.milliseconds

class SemantifyrVerifierConfiguration(
    val injector: Injector,
    val portfolio: VerificationPortfolio,
    val artifactConfig: ArtifactConfig,
    val outputDirectory: Path,
    val environment: ExecutionEnvironment,
    val timeout: Duration,
    val maxConcurrency: Int,
    val optimizationConfig: OptimizationConfig,
)

class SemantifyrVerifierImpl @AssistedInject constructor(
    @param:Assisted private val semantifyrVerifierConfiguration: SemantifyrVerifierConfiguration,
    private val classWitnessTransformer: ClassWitnessTransformer,
    private val classWitnessBackAnnotator: ClassWitnessBackAnnotator,
    private val callTraceTransformer: CallTraceTransformer,
    private val trivialVerifier: TrivialVerifier,
    private val verificationArtifactWriter: VerificationArtifactWriter,
) : SemantifyrVerifier {

    private val compiler = SemantifyrCompiler(
        semantifyrVerifierConfiguration.injector,
        semantifyrVerifierConfiguration.artifactConfig,
        semantifyrVerifierConfiguration.optimizationConfig,
    )

    private val logger by loggerFactory()

    private val gate = LimitedConcurrencyGate(
        semantifyrVerifierConfiguration.maxConcurrency,
    )

    override suspend fun verify(verificationCase: VerificationCase, progressContext: ProgressContext): VerificationResult {
        progressContext.reportProgress("Compiling ${verificationCase.qualifiedName}")
        logger.info { "Compiling '${verificationCase.qualifiedName}'" }
        val caseDirectory = prepareCaseDirectory(verificationCase.qualifiedName)
        val (compilation, compilationDuration) = measureTimedValue {
            compiler.compile(verificationCase.classDeclaration, caseDirectory)
        }

        return verifyCompilation(compilation, verificationCase, compilationDuration, progressContext)
    }

    override suspend fun verify(inlinedOxsts: InlinedOxsts, progressContext: ProgressContext): VerificationResult {
        val qualifiedName = inlinedOxsts.classDeclaration.name ?: "<inlined>"
        val case = VerificationCase(
            qualifiedName = qualifiedName,
            classDeclaration = inlinedOxsts.classDeclaration,
        )
        progressContext.reportProgress("Compiling $qualifiedName")
        logger.info { "Compiling inlined of '$qualifiedName'" }
        val caseDirectory = prepareCaseDirectory(qualifiedName)
        val (compilation, compilationDuration) = measureTimedValue {
            compiler.compile(inlinedOxsts, caseDirectory)
        }
        return verifyCompilation(compilation, case, compilationDuration, progressContext)
    }

    private suspend fun verifyCompilation(
        compilation: FlattenedCompilationContext,
        case: VerificationCase,
        compilationDuration: Duration,
        progress: ProgressContext,
    ): VerificationResult {
        val qualifiedName = case.qualifiedName
        val startedAt = Clock.System.now()
        logger.info { "Verifying case '$qualifiedName' with portfolio '${semantifyrVerifierConfiguration.portfolio.id}' (timeout=${semantifyrVerifierConfiguration.timeout})" }

        val (backendResult, portfolioDuration) = try {
            runInTimeout(progress) {
                runBackend(compilation, case, startedAt, progress)
            }
        } catch (_: TimeoutCancellationException) {
            logger.info { "Verification of '$qualifiedName' timed out after ${semantifyrVerifierConfiguration.timeout}, returning Inconclusive" }
            BackendOutcomeWithDuration(
                BackendVerificationResult(
                    metadata = VerificationMetadata(backendId = null, startedAt = startedAt),
                    verdict = VerificationVerdict.Inconclusive,
                    message = "Timeout (${semantifyrVerifierConfiguration.timeout}) exceeded.",
                ),
                Duration.ZERO,
            )
        } catch (exception: CancellationException) {
            logger.info { "Verification of '$qualifiedName' cancelled" }
            throw exception
        } catch (exception: BackendUnsupportedException) {
            logger.info(exception) { "Verification of '$qualifiedName' not supported by '${semantifyrVerifierConfiguration.portfolio.id}'." }
            BackendOutcomeWithDuration(
                BackendVerificationResult(
                    metadata = VerificationMetadata(backendId = null, startedAt = startedAt),
                    verdict = VerificationVerdict.NotSupported,
                    message = exception.message?.takeIf { it.isNotBlank() } ?: exception::class.simpleName,
                ),
                Duration.ZERO,
            )
        } catch (exception: Exception) {
            logger.warn(exception) { "Verification of '$qualifiedName' errored." }
            BackendOutcomeWithDuration(
                BackendVerificationResult(
                    metadata = VerificationMetadata(backendId = null, startedAt = startedAt),
                    verdict = VerificationVerdict.Errored,
                    message = exception.message?.takeIf { it.isNotBlank() } ?: exception::class.simpleName,
                ),
                Duration.ZERO,
            )
        }

        val caseArtifactPath = caseDirectoryFor(qualifiedName)

        val (trace, verifierBackAnnotationDuration) = backAnnotateWitness(backendResult, compilation, qualifiedName)

        val totalDuration = (Clock.System.now() - startedAt)

        val result = VerificationResult(
            metadata = backendResult.metadata.copy(startedAt = startedAt),
            verdict = backendResult.verdict,
            metrics = VerificationMetrics(
                totalDuration = totalDuration,
                backend = backendResult.metrics.takeIf { backendResult.metadata.backendId != null },
                verifier = VerifierMetrics(
                    compilationDuration = compilationDuration,
                    backAnnotationDuration = verifierBackAnnotationDuration,
                    portfolioDuration = portfolioDuration,
                ),
            ),
            trace = trace,
            message = backendResult.message,
        )

        logger.info { "Case '$qualifiedName' -> ${result.verdict} in $totalDuration - ${result.message ?: "no message"}" }

        val report = VerificationReport(
            verificationCase = case.qualifiedName,
            portfolioId = semantifyrVerifierConfiguration.portfolio.id,
            optimization = semantifyrVerifierConfiguration.optimizationConfig,
            timeout = semantifyrVerifierConfiguration.timeout,
            result = result,
        )

        verificationArtifactWriter.writeReport(caseArtifactPath, report)
        if (trace != null) {
            verificationArtifactWriter.writeWitnessArtifacts(trace, caseArtifactPath, qualifiedName)
        }

        return result
    }

    private suspend fun runBackend(
        compilation: FlattenedCompilationContext,
        case: VerificationCase,
        startedAt: Instant,
        progress: ProgressContext,
    ): BackendOutcomeWithDuration {
        val inlinedOxsts = compilation.inlinedOxsts

        if (trivialVerifier.isTriviallyVerifiable(inlinedOxsts)) {
            return BackendOutcomeWithDuration(
                trivialVerifier.verifyTrivially(inlinedOxsts, startedAt),
                Duration.ZERO,
            )
        }

        val request = BackendVerificationRequest(
            inlinedOxsts = inlinedOxsts,
            artifactOutputPath = caseDirectoryFor(case.qualifiedName),
        )

        val (result, duration) = measureTimedValue {
            semantifyrVerifierConfiguration.portfolio.verify(semantifyrVerifierConfiguration.injector, request, gate, semantifyrVerifierConfiguration.environment, progress)
        }
        return BackendOutcomeWithDuration(result, duration)
    }

    private fun backAnnotateWitness(
        backendResult: BackendVerificationResult,
        compilation: FlattenedCompilationContext,
        qualifiedName: String,
    ): Pair<Trace?, Duration> {
        val witness = backendResult.inlinedWitness ?: return null to Duration.ZERO

        logger.info { "[$qualifiedName] back-annotating witness from ${backendResult.metadata.backendId ?: "?"}" }
        val (trace, duration) = measureTimedValue {
            val classWitness = classWitnessTransformer.transform(witness, compilation)
            val backAnnotatedModel = classWitnessBackAnnotator.createWitnessInlinedOxsts(
                classWitness,
                backendResult.verdict,
            )
            val witnessState = callTraceTransformer.transformWitnessState(classWitness)
            val callTrace = callTraceTransformer.transformCallTrace(classWitness, compilation.transitionCallTraces)
            Trace(backAnnotatedModel, witnessState, callTrace)
        }
        logger.info { "[$qualifiedName] back-annotation completed in $duration" }
        return trace to duration
    }

    private suspend inline fun <T> runInTimeout(
        progress: ProgressContext,
        crossinline block: suspend () -> T,
    ): T = coroutineScope {
        val mainWork = async {
            withTimeout(semantifyrVerifierConfiguration.timeout) {
                block()
            }
        }
        val cancelPoller = launch {
            while (isActive) {
                delay(CANCEL_POLL_INTERVAL)
                try {
                    progress.checkIsCancelled()
                } catch (c: CancellationException) {
                    mainWork.cancel(c)
                    return@launch
                }
            }
        }
        try {
            mainWork.await()
        } finally {
            cancelPoller.cancel()
        }
    }

    private fun caseDirectoryFor(qualifiedName: String): Path {
        return semantifyrVerifierConfiguration.outputDirectory.resolve(qualifiedNameToDirectoryName(qualifiedName))
    }

    private fun prepareCaseDirectory(qualifiedName: String): Path {
        val caseDirectory = caseDirectoryFor(qualifiedName)
        if (caseDirectory.toFile().exists()) {
            caseDirectory.toFile().deleteRecursively()
        }
        caseDirectory.createDirectories()
        return caseDirectory
    }

    private data class BackendOutcomeWithDuration(
        val result: BackendVerificationResult,
        val portfolioDuration: Duration,
    )

    interface Factory {
        fun create(
            configuration: SemantifyrVerifierConfiguration,
        ): SemantifyrVerifierImpl
    }

}
