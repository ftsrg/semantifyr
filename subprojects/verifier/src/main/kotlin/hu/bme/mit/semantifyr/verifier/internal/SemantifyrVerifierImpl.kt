/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.internal

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
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifier
import hu.bme.mit.semantifyr.verifier.Trace
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.VerificationMetrics
import hu.bme.mit.semantifyr.verifier.VerificationReport
import hu.bme.mit.semantifyr.verifier.VerificationResult
import hu.bme.mit.semantifyr.verifier.VerifierMetrics
import hu.bme.mit.semantifyr.verifier.caseDirectoryName
import hu.bme.mit.semantifyr.verifier.caseReportPath
import hu.bme.mit.semantifyr.verifier.portfolio.LimitedConcurrencyGate
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import hu.bme.mit.semantifyr.verifier.witness.CallTraceTransformer
import hu.bme.mit.semantifyr.verifier.witness.ClassWitnessBackAnnotator
import hu.bme.mit.semantifyr.verifier.witness.ClassWitnessTransformer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.createDirectories
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.time.TimeSource.Monotonic.ValueTimeMark
import kotlin.time.TimeSource.Monotonic.markNow
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
        val deadline = markNow() + semantifyrVerifierConfiguration.timeout
        progressContext.reportProgress("Compiling ${verificationCase.qualifiedName}")
        logger.info { "Compiling '${verificationCase.qualifiedName}'" }
        val caseDirectory = prepareCaseDirectory(verificationCase)
        return compileAndVerify(verificationCase, progressContext, deadline) {
            compiler.compile(verificationCase.classDeclaration, caseDirectory)
        }
    }

    override suspend fun verify(inlinedOxsts: InlinedOxsts, progressContext: ProgressContext): VerificationResult {
        val qualifiedName = inlinedOxsts.classDeclaration.name ?: "<inlined>"
        val case = VerificationCase(
            qualifiedName = qualifiedName,
            classDeclaration = inlinedOxsts.classDeclaration,
        )
        val deadline = markNow() + semantifyrVerifierConfiguration.timeout
        progressContext.reportProgress("Compiling $qualifiedName")
        logger.info { "Compiling inlined of '$qualifiedName'" }
        val caseDirectory = prepareCaseDirectory(case)
        return compileAndVerify(case, progressContext, deadline) {
            compiler.compile(inlinedOxsts, caseDirectory)
        }
    }

    // the requested timeout bounds the whole case: compilation, the portfolio's legs and the back-annotation
    private suspend fun compileAndVerify(
        case: VerificationCase,
        progress: ProgressContext,
        deadline: ValueTimeMark,
        compile: () -> FlattenedCompilationContext,
    ): VerificationResult {
        val compilationMark = markNow()
        val compilation = try {
            runInTimeout(progress, deadline) {
                runInterruptible {
                    compile()
                }
            }
        } catch (exception: Exception) {
            // an interrupt can also surface as an I/O failure, from a file the compilation was writing
            if (exception !is TimeoutCancellationException && (exception is CancellationException || !deadline.hasPassedNow())) {
                throw exception
            }
            logger.info { "Compilation of '${case.qualifiedName}' timed out after ${semantifyrVerifierConfiguration.timeout}" }
            val result = VerificationResult(
                metadata = VerificationMetadata(backendId = null, startedAt = Clock.System.now()),
                verdict = VerificationVerdict.Timeout,
                metrics = VerificationMetrics(
                    verifier = VerifierMetrics(compilationDuration = compilationMark.elapsedNow()),
                ),
                message = "Timeout (${semantifyrVerifierConfiguration.timeout}) exceeded while compiling.",
            )
            writeReport(case, result)
            return result
        }
        return verifyCompilation(compilation, case, progress, deadline)
    }

    private suspend fun verifyCompilation(
        compilation: FlattenedCompilationContext,
        case: VerificationCase,
        progress: ProgressContext,
        deadline: ValueTimeMark,
    ): VerificationResult {
        val qualifiedName = case.qualifiedName
        val startedAt = Clock.System.now()
        logger.info { "Verifying case '$qualifiedName' with portfolio '${semantifyrVerifierConfiguration.portfolio.id}' (timeout=${semantifyrVerifierConfiguration.timeout})" }

        val portfolioMark = markNow()
        val (backendResult, portfolioDuration) = try {
            runInTimeout(progress, deadline) {
                runBackend(compilation, case, startedAt, progress, deadline)
            }
        } catch (_: TimeoutCancellationException) {
            logger.info { "Verification of '$qualifiedName' timed out after ${semantifyrVerifierConfiguration.timeout}" }
            BackendOutcomeWithDuration(
                BackendVerificationResult(
                    metadata = VerificationMetadata(backendId = null, startedAt = startedAt),
                    verdict = VerificationVerdict.Timeout,
                    message = "Timeout (${semantifyrVerifierConfiguration.timeout}) exceeded.",
                ),
                portfolioMark.elapsedNow(),
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
                portfolioMark.elapsedNow(),
            )
        } catch (exception: Exception) {
            logger.warn(exception) { "Verification of '$qualifiedName' errored." }
            BackendOutcomeWithDuration(
                BackendVerificationResult(
                    metadata = VerificationMetadata(backendId = null, startedAt = startedAt),
                    verdict = VerificationVerdict.Errored,
                    message = exception.message?.takeIf { it.isNotBlank() } ?: exception::class.simpleName,
                ),
                portfolioMark.elapsedNow(),
            )
        }

        val caseArtifactPath = caseDirectoryFor(case)

        val backAnnotationMark = markNow()
        val (outcome, trace, verifierBackAnnotationDuration) = try {
            val (trace, duration) = runInTimeout(progress, deadline) {
                runInterruptible {
                    backAnnotateWitness(backendResult, compilation, qualifiedName)
                }
            }
            Triple(backendResult, trace, duration)
        } catch (_: TimeoutCancellationException) {
            logger.info { "Back-annotation of '$qualifiedName' timed out after ${semantifyrVerifierConfiguration.timeout}" }
            val timedOut = backendResult.copy(
                verdict = VerificationVerdict.Timeout,
                isTrusted = false,
                inlinedWitness = null,
                message = "Timeout (${semantifyrVerifierConfiguration.timeout}) exceeded while back-annotating the witness of ${backendResult.metadata.backendId}'s ${backendResult.verdict} verdict.",
            )
            Triple(timedOut, null, backAnnotationMark.elapsedNow())
        }

        val totalDuration = (Clock.System.now() - startedAt)

        val result = VerificationResult(
            metadata = outcome.metadata.copy(startedAt = startedAt),
            verdict = outcome.verdict,
            soundness = outcome.soundness,
            isTrusted = outcome.isTrusted,
            metrics = VerificationMetrics(
                totalDuration = totalDuration,
                backend = backendResult.metrics.takeIf { backendResult.metadata.backendId != null },
                verifier = VerifierMetrics(
                    compilationDuration = compilation.compilationMetrics.unfoldingDuration,
                    compilation = compilation.compilationMetrics,
                    backAnnotationDuration = verifierBackAnnotationDuration,
                    portfolioDuration = portfolioDuration,
                ),
            ),
            trace = trace,
            message = outcome.message,
        )

        logger.info { "Case '$qualifiedName' -> ${result.verdict} in $totalDuration - ${result.message ?: "no message"}" }

        writeReport(case, result)
        if (trace != null) {
            verificationArtifactWriter.writeWitnessArtifacts(trace, caseArtifactPath, qualifiedName)
        }

        return result
    }

    private fun writeReport(case: VerificationCase, result: VerificationResult) {
        val report = VerificationReport(
            verificationCase = case.qualifiedName,
            portfolioId = semantifyrVerifierConfiguration.portfolio.id,
            optimization = semantifyrVerifierConfiguration.optimizationConfig,
            timeout = semantifyrVerifierConfiguration.timeout,
            result = result,
        )
        verificationArtifactWriter.writeReport(caseReportPath(semantifyrVerifierConfiguration.outputDirectory, case), report)
    }

    private suspend fun runBackend(
        compilation: FlattenedCompilationContext,
        case: VerificationCase,
        startedAt: Instant,
        progress: ProgressContext,
        deadline: ValueTimeMark,
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
            artifactOutputPath = caseDirectoryFor(case),
            flatteningInfo = compilation.flatteningInfo,
            timeout = -deadline.elapsedNow(),
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
        deadline: ValueTimeMark,
        crossinline block: suspend () -> T,
    ): T = coroutineScope {
        val mainWork = async {
            withTimeout(-deadline.elapsedNow()) {
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

    private fun caseDirectoryFor(case: VerificationCase): Path {
        return semantifyrVerifierConfiguration.outputDirectory.resolve(caseDirectoryName(case))
    }

    private fun prepareCaseDirectory(case: VerificationCase): Path {
        val caseDirectory = caseDirectoryFor(case)
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
