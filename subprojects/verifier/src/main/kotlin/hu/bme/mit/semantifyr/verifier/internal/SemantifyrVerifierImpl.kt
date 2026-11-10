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
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import hu.bme.mit.semantifyr.oxsts.lang.utils.SelfContainedResource
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
import hu.bme.mit.semantifyr.verifier.portfolio.LimitedConcurrencyGate
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import hu.bme.mit.semantifyr.verifier.reportPath
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
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.util.EcoreUtil
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
    val artifactConfiguration: ArtifactConfig,
    val outputDirectory: Path,
    val environment: ExecutionEnvironment,
    val timeout: Duration,
    val maxConcurrency: Int,
    val optimizationConfiguration: OptimizationConfig,
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
        semantifyrVerifierConfiguration.artifactConfiguration,
        semantifyrVerifierConfiguration.optimizationConfiguration,
    )

    private val logger by loggerFactory()

    private val gate = LimitedConcurrencyGate(
        semantifyrVerifierConfiguration.maxConcurrency,
    )

    override suspend fun verify(verificationCase: VerificationCase, progressContext: ProgressContext): VerificationResult {
        val deadline = markNow() + semantifyrVerifierConfiguration.timeout
        progressContext.reportProgress("Compiling ${verificationCase.qualifiedName}")
        logger.info { "Compiling '${verificationCase.qualifiedName}'" }
        val caseDirectory = prepareDirectory(caseDirectoryFor(verificationCase))
        return compileAndVerify(verificationCase, caseDirectory, progressContext, deadline) {
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
        // the model re-verified here usually lies in the case directory, a witness the verify left there, so the run keeps out of the verify's artifacts
        val validationDirectory = prepareDirectory(caseDirectoryFor(case).resolve("validation"))
        val copy = copyIntoOwnResource(inlinedOxsts, validationDirectory)
        return compileAndVerify(case, validationDirectory, progressContext, deadline) {
            compiler.compile(copy, validationDirectory)
        }
    }

    // compiling rewrites the inlined model in place, and the original may be a document open in an editor
    private fun copyIntoOwnResource(inlinedOxsts: InlinedOxsts, directory: Path): InlinedOxsts {
        val resourceSet = inlinedOxsts.eResource().resourceSet
        val uri = URI.createFileURI(directory.resolve("inlined.oxsts").toAbsolutePath().toString())
        resourceSet.getResource(uri, false)?.let {
            check(it !== inlinedOxsts.eResource()) {
                "Cannot re-verify the inlined model at $uri into its own directory"
            }
            resourceSet.resources.remove(it)
        }
        val copy = EcoreUtil.copy(inlinedOxsts)
        val resource = resourceSet.createResource(uri)
        SelfContainedResource.mark(resource)
        resource.contents += copy
        return copy
    }

    private suspend fun compileAndVerify(
        case: VerificationCase,
        caseDirectory: Path,
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
            logger.info {
                "Compilation of '${case.qualifiedName}' timed out after ${semantifyrVerifierConfiguration.timeout}"
            }
            val result = VerificationResult(
                metadata = VerificationMetadata(backendId = null, startedAt = Clock.System.now()),
                verdict = VerificationVerdict.Timeout,
                metrics = VerificationMetrics(
                    verifier = VerifierMetrics(compilationDuration = compilationMark.elapsedNow()),
                ),
                message = "Timeout (${semantifyrVerifierConfiguration.timeout}) exceeded while compiling.",
            )
            writeReport(case, caseDirectory, result)
            return result
        }
        return verifyCompilation(compilation, case, caseDirectory, progress, deadline)
    }

    private suspend fun verifyCompilation(
        compilation: FlattenedCompilationContext,
        case: VerificationCase,
        caseDirectory: Path,
        progress: ProgressContext,
        deadline: ValueTimeMark,
    ): VerificationResult {
        val qualifiedName = case.qualifiedName
        val startedAt = Clock.System.now()
        logger.info { "Verifying case '$qualifiedName' with portfolio '${semantifyrVerifierConfiguration.portfolio.id}' (timeout=${semantifyrVerifierConfiguration.timeout})" }

        val portfolioMark = markNow()
        val (backendResult, portfolioDuration) = try {
            runInTimeout(progress, deadline) {
                runBackend(compilation, caseDirectory, startedAt, progress, deadline)
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

        val backAnnotationMark = markNow()
        val (outcome, trace, verifierBackAnnotationDuration) = try {
            val witness = backendResult.inlinedWitness
            if (witness == null) {
                BackAnnotationOutcome(backendResult, null, Duration.ZERO)
            } else {
                val trace = runInTimeout(progress, deadline) {
                    runInterruptible {
                        backAnnotateWitness(witness, backendResult, compilation, qualifiedName)
                    }
                }
                val duration = backAnnotationMark.elapsedNow()
                logger.info { "[$qualifiedName] back-annotation completed in $duration" }
                BackAnnotationOutcome(backendResult, trace, duration)
            }
        } catch (_: TimeoutCancellationException) {
            logger.info {
                "Back-annotation of '$qualifiedName' timed out after ${semantifyrVerifierConfiguration.timeout}"
            }
            val timedOut = backendResult.copy(
                verdict = VerificationVerdict.Timeout,
                isTrusted = false,
                inlinedWitness = null,
                message = "Timeout (${semantifyrVerifierConfiguration.timeout}) exceeded while back-annotating the witness of ${backendResult.metadata.backendId}'s ${backendResult.verdict} verdict.",
            )
            BackAnnotationOutcome(timedOut, null, backAnnotationMark.elapsedNow())
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

        writeReport(case, caseDirectory, result)
        if (trace != null) {
            verificationArtifactWriter.writeWitnessArtifacts(trace, caseDirectory, qualifiedName)
        }

        return result
    }

    private fun writeReport(
        case: VerificationCase,
        caseDirectory: Path,
        result: VerificationResult,
    ) {
        val report = VerificationReport(
            verificationCase = case.qualifiedName,
            portfolioId = semantifyrVerifierConfiguration.portfolio.id,
            optimization = semantifyrVerifierConfiguration.optimizationConfiguration,
            timeout = semantifyrVerifierConfiguration.timeout,
            result = result,
        )
        verificationArtifactWriter.writeReport(reportPath(caseDirectory), report)
    }

    private suspend fun runBackend(
        compilation: FlattenedCompilationContext,
        caseDirectory: Path,
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
            artifactOutputPath = caseDirectory,
            flatteningInfo = compilation.flatteningInfo,
            timeout = deadline - markNow(),
        )

        val (result, duration) = measureTimedValue {
            semantifyrVerifierConfiguration.portfolio.verify(semantifyrVerifierConfiguration.injector, request, gate, semantifyrVerifierConfiguration.environment, progress)
        }
        return BackendOutcomeWithDuration(result, duration)
    }

    private fun backAnnotateWitness(
        witness: InlinedWitness,
        backendResult: BackendVerificationResult,
        compilation: FlattenedCompilationContext,
        qualifiedName: String,
    ): Trace {
        logger.info { "[$qualifiedName] back-annotating witness from ${backendResult.metadata.backendId ?: "?"}" }
        val classWitness = classWitnessTransformer.transform(witness, compilation)
        val backAnnotatedModel = classWitnessBackAnnotator.createWitnessInlinedOxsts(
            classWitness,
            backendResult.verdict,
        )
        val witnessState = callTraceTransformer.transformWitnessState(classWitness)
        val callTrace = callTraceTransformer.transformCallTrace(classWitness, compilation.transitionCallTraces)
        return Trace(backAnnotatedModel, witnessState, callTrace)
    }

    private suspend inline fun <T> runInTimeout(
        progress: ProgressContext,
        deadline: ValueTimeMark,
        crossinline block: suspend () -> T,
    ): T = coroutineScope {
        val mainWork = async {
            withTimeout(deadline - markNow()) {
                block()
            }
        }
        val cancelPoller = launch {
            while (isActive) {
                delay(CANCEL_POLL_INTERVAL)
                try {
                    progress.checkIsCancelled()
                } catch (cancellation: CancellationException) {
                    mainWork.cancel(cancellation)
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

    private fun prepareDirectory(directory: Path): Path {
        if (directory.toFile().exists()) {
            directory.toFile().deleteRecursively()
        }
        directory.createDirectories()
        return directory
    }

    private data class BackendOutcomeWithDuration(
        val result: BackendVerificationResult,
        val portfolioDuration: Duration,
    )

    private data class BackAnnotationOutcome(
        val result: BackendVerificationResult,
        val trace: Trace?,
        val backAnnotationDuration: Duration,
    )

    interface Factory {
        fun create(
            configuration: SemantifyrVerifierConfiguration,
        ): SemantifyrVerifierImpl
    }

}
