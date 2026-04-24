/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.internal

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.VerificationCase
import hu.bme.mit.semantifyr.backend.VerificationMetrics
import hu.bme.mit.semantifyr.backend.VerificationRequest
import hu.bme.mit.semantifyr.backend.VerificationResult
import hu.bme.mit.semantifyr.backend.VerificationRunMetadata
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.BooleanEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.verification.ProgressContext
import hu.bme.mit.semantifyr.verification.SemantifyrVerifier
import hu.bme.mit.semantifyr.verification.discovery.CaseFilter
import hu.bme.mit.semantifyr.verification.discovery.VerificationCaseDiscoverer
import hu.bme.mit.semantifyr.verification.portfolio.VerificationPortfolio
import hu.bme.mit.semantifyr.verification.portfolio.LimitedBackendExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val CANCEL_POLL_INTERVAL = 250.milliseconds

class SemantifyrVerifierImpl(
    private val injector: Injector,
    private val context: SemantifyrModelContext,
    private val verificationPortfolio: VerificationPortfolio,
    private val artifactConfig: ArtifactConfig,
    private val environment: ExecutionEnvironment,
    private val timeout: Duration,
    maxConcurrency: Int,
    private val optimizationConfig: OptimizationConfig,
) : SemantifyrVerifier {

    private val logger by loggerFactory()

    private val discoverer = injector.getInstance(VerificationCaseDiscoverer::class.java)

    private val constantEvaluatorProvider = injector.getInstance(ConstantExpressionEvaluatorProvider::class.java)

    private val executor = LimitedBackendExecutor(maxConcurrency)

    private val compiler = SemantifyrCompiler(injector, artifactConfig, optimizationConfig)

    private val cases by lazy {
        val discovered = discoverer.discover(context)
        logger.info { "Discovered ${discovered.size} verification case(s) in the loaded model" }
        discovered
    }

    override fun modelContext(): SemantifyrModelContext {
        return context
    }

    override fun verificationCases(filter: CaseFilter): List<VerificationCase> {
        return cases.filter(filter::matches)
    }

    override suspend fun verifyAll(filter: CaseFilter, progressContext: ProgressContext): List<VerificationResult> {
        val results = mutableListOf<VerificationResult>()
        for (case in verificationCases(filter)) {
            progressContext.checkIsCancelled()
            results += verify(case, progressContext.child(case.qualifiedName))
        }
        return results
    }

    override suspend fun verify(qualifiedName: String, progressContext: ProgressContext): VerificationResult {
        logger.info { "Looking for verification case named $qualifiedName" }

        val case = cases.firstOrNull {
            it.qualifiedName == qualifiedName
        }

        if (case == null) {
            return caseNotFound(qualifiedName)
        }

        return verify(case, progressContext)
    }

    private fun caseNotFound(qualifiedName: String): VerificationResult {
        val known = cases.map {
            it.qualifiedName
        }
        val knownSummary = if (known.size <= 10) {
            known.toString()
        } else {
            "${known.take(10)} (+ ${known.size - 10} more)"
        }

        logger.warn { "No verification case with qualified name '$qualifiedName' (known: $knownSummary)" }
        return VerificationResult(
            verdict = VerificationVerdict.Errored,
            metadata = VerificationRunMetadata(
                backendId = "none",
                startedAt = Clock.System.now(),
                caseQualifiedName = qualifiedName,
            ),
            message = "No verification case named '$qualifiedName'.",
        )
    }

    override suspend fun verify(verificationCase: VerificationCase, progressContext: ProgressContext): VerificationResult {
        progressContext.reportProgress("Compiling ${verificationCase.qualifiedName}")
        logger.info { "Compiling '${verificationCase.qualifiedName}'" }
        val compilation = compiler.compile(verificationCase.classDeclaration)
        return verify(compilation, verificationCase, progressContext)
    }

    override suspend fun verify(inlinedOxsts: InlinedOxsts, progressContext: ProgressContext): VerificationResult {
        val qualifiedName = inlinedOxsts.classDeclaration.name ?: "<inlined>"
        val case = VerificationCase(
            classDeclaration = inlinedOxsts.classDeclaration,
            qualifiedName = qualifiedName,
        )
        progressContext.reportProgress("Compiling $qualifiedName")
        logger.info { "Compiling inlined of '$qualifiedName'" }
        val compilation = compiler.compile(inlinedOxsts)
        return verify(compilation, case, progressContext)
    }

    private suspend fun verify(
        compilation: FlattenedCompilationContext,
        case: VerificationCase,
        progress: ProgressContext,
    ): VerificationResult {
        val qualifiedName = case.qualifiedName
        logger.info { "Verifying case '$qualifiedName' with portfolio '${verificationPortfolio.id}' (timeout=$timeout)" }

        return try {
            runInTimeout(progress) {
                runVerification(compilation, case, progress)
            }
        } catch (e: TimeoutCancellationException) {
            logger.info { "Verification of '$qualifiedName' timed out after $timeout; returning Inconclusive" }
            VerificationResult(
                verdict = VerificationVerdict.Inconclusive,
                metadata = VerificationRunMetadata(
                    backendId = verificationPortfolio.id,
                    startedAt = Clock.System.now(),
                    caseQualifiedName = qualifiedName,
                ),
                message = "Timeout ($timeout) exceeded.",
            )
        } catch (c: CancellationException) {
            logger.info { "Verification of '$qualifiedName' cancelled" }
            throw c
        } catch (e: Exception) {
            logger.warn("Verification of '$qualifiedName' errored: ${e.message ?: e::class.simpleName}", e)
            VerificationResult(
                verdict = VerificationVerdict.Errored,
                metadata = VerificationRunMetadata(
                    backendId = verificationPortfolio.id,
                    startedAt = Clock.System.now(),
                    caseQualifiedName = qualifiedName,
                ),
                message = e.message ?: e::class.simpleName,
            )
        }
    }

    private suspend fun runVerification(
        compilationContext: FlattenedCompilationContext,
        verificationCase: VerificationCase,
        progressContext: ProgressContext,
    ): VerificationResult {
        val qualifiedName = verificationCase.qualifiedName
        val inlinedOxsts = compilationContext.inlinedOxsts

        if (isTriviallyVerifiable(inlinedOxsts)) {
            return trivialVerdict(qualifiedName, inlinedOxsts)
        }

        val request = VerificationRequest(
            case = verificationCase,
            input = inlinedOxsts,
            compilation = compilationContext,
            artifactOutputPath = artifactConfig.outputDirectory.resolve(qualifiedName.replace("::", ".")),
        )

        val result = verificationPortfolio.verify(request, executor, environment, progressContext)

        logger.info { "Case '$qualifiedName' -> ${result.verdict} in ${result.metrics.totalDuration} - ${result.message ?: "no message"}" }

        return result
    }

    private suspend inline fun <T> runInTimeout(
        progress: ProgressContext,
        crossinline block: suspend () -> T,
    ): T = coroutineScope {
        val mainWork = async {
            withTimeout(timeout) {
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

    override fun close() {
        compiler.close()
    }

    private fun isTriviallyVerifiable(inlinedOxsts: InlinedOxsts): Boolean {
        return inlinedOxsts.variables.isEmpty()
            && inlinedOxsts.initTransition.isEmpty()
            && inlinedOxsts.mainTransition.isEmpty()
    }

    private fun TransitionDeclaration.isEmpty(): Boolean {
        if (branches.size > 1) {
            return false
        }
        val branch = branches.single()
        return branch.steps.isEmpty()
    }

    private fun trivialVerdict(qualifiedName: String, inlinedOxsts: InlinedOxsts): VerificationResult {
        val body = when (val expression = inlinedOxsts.property.expression) {
            is AG -> expression.body
            is EF -> expression.body
            else -> error("Specified property is not in the expected form!")
        }

        val evaluation = constantEvaluatorProvider.evaluate(body)
        require(evaluation is BooleanEvaluation) {
            "Trivially verifiable model's property body did not evaluate to a boolean (got ${evaluation::class.simpleName})"
        }

        val verdict = if (evaluation.value()) {
            VerificationVerdict.Passed
        } else {
            VerificationVerdict.Failed
        }

        logger.info { "Trivial $verdict verdict (model optimized away)." }
        return VerificationResult(
            verdict = verdict,
            metadata = VerificationRunMetadata(
                backendId = "trivial",
                startedAt = Clock.System.now(),
                caseQualifiedName = qualifiedName,
            ),
            metrics = VerificationMetrics(),
            message = "Property decided at compile time; model fully optimized away.",
        )
    }

}
