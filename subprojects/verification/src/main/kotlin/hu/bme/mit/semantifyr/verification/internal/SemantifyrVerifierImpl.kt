/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.internal

import hu.bme.mit.semantifyr.backend.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.VerificationCase
import hu.bme.mit.semantifyr.backend.VerificationRequest
import hu.bme.mit.semantifyr.backend.VerificationResult
import hu.bme.mit.semantifyr.backend.VerificationRunMetadata
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.verification.ProgressContext
import hu.bme.mit.semantifyr.verification.SemantifyrVerifier
import hu.bme.mit.semantifyr.verification.discovery.CaseFilter
import hu.bme.mit.semantifyr.verification.discovery.VerificationCaseDiscoverer
import hu.bme.mit.semantifyr.verification.portfolio.BackendExecutor
import hu.bme.mit.semantifyr.verification.portfolio.VerificationPortfolio
import hu.bme.mit.semantifyr.verification.portfolio.LimitedBackendExecutor
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Default [SemantifyrVerifier] implementation.
 *
 * Owns a [SemantifyrCompiler] and a concurrency-gated [BackendExecutor] for the
 * lifetime of one verification session. Discovered cases are cached on first use.
 */
class SemantifyrVerifierImpl(
    private val context: SemantifyrModelContext,
    private val verificationPortfolio: VerificationPortfolio,
    private val artifacts: ArtifactConfig,
    private val environment: ExecutionEnvironment,
    private val timeout: Duration,
    maxConcurrency: Int,
    private val optimization: OptimizationConfig,
) : SemantifyrVerifier {

    private val logger by loggerFactory()

    private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()

    private val discoverer = injector.getInstance(VerificationCaseDiscoverer::class.java)

    private val executor = LimitedBackendExecutor(maxConcurrency)

    private val compiler = SemantifyrCompiler(artifacts, optimization)

    private val cases: List<VerificationCase> by lazy {
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

    override suspend fun verify(case: VerificationCase, progress: ProgressContext): VerificationResult {
        logger.info { "Verifying case '${case.qualifiedName}' with portfolio '${verificationPortfolio.id}' (timeout=$timeout)" }

        val workingDir = artifacts.outputDirectory.resolve(workingDirectoryName(case.qualifiedName))
        Files.createDirectories(workingDir)

        return try {
            progress.reportProgress("Compiling ${case.qualifiedName}")
            progress.checkIsCancelled()

            val compilation = compiler.compile(case.classDeclaration)

            val request = VerificationRequest(
                case = case,
                input = compilation.inlinedOxsts,
                artifactOutputPath = workingDir,
            )
            val result = withTimeout(timeout) {
                verificationPortfolio.verify(request, executor, environment, progress)
            }
            progress.reportProgress("Verification finished - ${result.verdict}")
            logger.info {
                "Case '${case.qualifiedName}' -> ${result.verdict} in ${result.metrics.totalDuration}" +
                    (result.message?.let { " ($it)" } ?: "")
            }
            result
        } catch (e: Exception) {
            logger.warn { "Verification of '${case.qualifiedName}' errored: ${e.message ?: e::class.simpleName}" }
            VerificationResult(
                verdict = VerificationVerdict.Errored,
                metadata = VerificationRunMetadata(
                    backendId = verificationPortfolio.id,
                    startedAt = Clock.System.now(),
                    caseQualifiedName = case.qualifiedName,
                ),
                message = e.message ?: e::class.simpleName,
            )
        }
    }

    override suspend fun verify(qualifiedName: String, progress: ProgressContext): VerificationResult {
        val case = cases.firstOrNull { it.qualifiedName == qualifiedName }
            ?: error("No verification case with qualified name '$qualifiedName' (known: ${cases.map { it.qualifiedName }})")
        return verify(case, progress)
    }

    override suspend fun verifyAll(filter: CaseFilter, progress: ProgressContext): List<VerificationResult> {
        val results = mutableListOf<VerificationResult>()
        for (case in verificationCases(filter)) {
            progress.checkIsCancelled()
            results += verify(case, progress.child(case.qualifiedName))
        }
        return results
    }

    override suspend fun verify(inlinedOxsts: InlinedOxsts, progress: ProgressContext): VerificationResult {
        val qualifiedName = inlinedOxsts.classDeclaration.name ?: "<inlined>"
        logger.info { "Verifying inlined OXSTS for '$qualifiedName' with portfolio '${verificationPortfolio.id}' (timeout=$timeout)" }

        val case = VerificationCase(
            classDeclaration = inlinedOxsts.classDeclaration,
            qualifiedName = qualifiedName,
        )
        val workingDir = artifacts.outputDirectory.resolve(workingDirectoryName(qualifiedName))
        Files.createDirectories(workingDir)

        return try {
            progress.reportProgress("compiling inlined OXSTS")
            progress.checkIsCancelled()
            val compilation = compiler.compile(inlinedOxsts)

            progress.checkIsCancelled()
            progress.reportProgress("dispatching to portfolio '${verificationPortfolio.id}'")
            val request = VerificationRequest(
                case = case,
                input = compilation.inlinedOxsts,
                artifactOutputPath = workingDir,
            )
            val result = withTimeout(timeout) {
                verificationPortfolio.verify(request, executor, environment, progress)
            }
            progress.reportProgress("finished: ${result.verdict}")
            logger.info { "Inlined OXSTS '$qualifiedName' -> ${result.verdict}" }
            result
        } catch (e: Exception) {
            logger.warn { "Verification of inlined OXSTS '$qualifiedName' errored: ${e.message ?: e::class.simpleName}" }
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

    override fun close() {
        compiler.close()
    }

    // Qualified names contain '::' separators which confuse Docker's volume-spec parser
    // when passed through as a bind-mount host path. Flatten to dots for filesystem use.
    private fun workingDirectoryName(qualifiedName: String): String {
        return qualifiedName.replace("::", ".")
    }
}
