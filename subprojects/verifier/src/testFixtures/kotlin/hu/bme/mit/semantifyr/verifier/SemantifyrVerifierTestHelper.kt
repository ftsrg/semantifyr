/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendConfig
import hu.bme.mit.semantifyr.backend.SoundnessDirection
import hu.bme.mit.semantifyr.backend.VerificationBackend
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.scopes.withVerificationScopeBlocking
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.verifier.discovery.CaseFilter
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.provider.Arguments
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class SemantifyrVerifierTestHelper @Inject constructor(
    val injector: Injector,
    val semantifyrLoader: SemantifyrLoader,
    val verificationCaseDiscoverer: VerificationCaseDiscoverer,
) {

    val logger by loggerFactory()

    private val compiledProperties = mutableMapOf<ClassDeclaration, PropertyDeclaration?>()

    private fun buildVerifier(
        verificationPortfolio: VerificationPortfolio,
        timeout: Duration,
        outputDirectory: Path,
        environment: ExecutionEnvironment = ExecutionEnvironment.Empty,
        artifacts: ArtifactConfig = ArtifactConfig.ALL,
        optimization: OptimizationConfig? = null,
    ): SemantifyrVerifier {
        outputDirectory.createDirectories()
        val builder = SemantifyrVerifier.builder()
            .injector(injector)
            .portfolio(verificationPortfolio)
            .environment(environment)
            .artifacts(artifacts)
            .outputDirectory(outputDirectory)
            .timeout(timeout)
        optimization?.let {
            builder.optimization(it)
        }
        return builder.build()
    }

    fun collectVerificationCases(
        context: SemantifyrModelContext,
        filter: CaseFilter = CaseFilter.All,
    ): List<VerificationCase> {
        return verificationCaseDiscoverer.discover(context, filter)
    }

    fun collectVerificationCasesAsArguments(
        context: SemantifyrModelContext,
        filter: CaseFilter = CaseFilter.All,
    ): List<Arguments> {
        return collectVerificationCases(context, filter).map {
            Arguments.of(Named.of(it.qualifiedName, it))
        }
    }

    fun <T : BackendConfig> collectAcceptedCasesAsArguments(
        context: SemantifyrModelContext,
        backend: VerificationBackend<T>,
        config: T,
        outputDirectory: Path,
        filter: CaseFilter = CaseFilter.All,
    ): List<Arguments> {
        return collectSupportedCases(context, backend, config, outputDirectory, filter).filter {
            if (it.loweredExactly) {
                return@filter true
            }
            logger.info { "Leaving out ${it.case.qualifiedName}: ${backend.id} approximates the model in its lowering" }
            false
        }.map {
            Arguments.of(Named.of(it.case.qualifiedName, it.case))
        }
    }

    fun <T : BackendConfig> collectSupportedCasesAsArguments(
        context: SemantifyrModelContext,
        backend: VerificationBackend<T>,
        config: T,
        outputDirectory: Path,
        filter: CaseFilter = CaseFilter.All,
    ): List<Arguments> {
        return collectSupportedCases(context, backend, config, outputDirectory, filter).map {
            Arguments.of(Named.of(it.case.qualifiedName, it.case))
        }
    }

    private class SupportedCase(
        val case: VerificationCase,
        val loweredExactly: Boolean,
    )

    private fun <T : BackendConfig> collectSupportedCases(
        context: SemantifyrModelContext,
        backend: VerificationBackend<T>,
        config: T,
        outputDirectory: Path,
        filter: CaseFilter,
    ): List<SupportedCase> {
        val compiler = SemantifyrCompiler(injector, ArtifactConfig.NONE)
        return collectVerificationCases(context, filter).mapNotNull { case ->
            val compiled = compiler.compile(case.classDeclaration, outputDirectory.resolve(caseDirectoryName(case)))
            val inlinedOxsts = compiled.inlinedOxsts
            withVerificationScopeBlocking {
                val checker = backend.capabilityChecker(injector, config)
                val unsupported = checker.check(inlinedOxsts, compiled.transitionCallTraces)
                if (unsupported.isEmpty()) {
                    SupportedCase(case, checker.soundness(SoundnessDirection.Exact, inlinedOxsts) == SoundnessDirection.Exact)
                } else {
                    logger.info {
                        val refusals = unsupported.map {
                            it.description
                        }.distinct().joinToString()
                        "Leaving out ${case.qualifiedName}: ${backend.id} refuses $refusals"
                    }
                    null
                }
            }
        }
    }

    suspend fun checkVerificationCase(
        context: SemantifyrModelContext,
        case: VerificationCase,
        verificationPortfolio: VerificationPortfolio,
        outputDirectory: Path,
        timeout: Duration = 30.minutes,
        environment: ExecutionEnvironment = ExecutionEnvironment.Empty,
        expectedVerdict: VerificationVerdict,
        optimization: OptimizationConfig? = null,
        validationPortfolio: VerificationPortfolio? = null,
    ) {
        val outcome = runVerificationCase(context, case, verificationPortfolio, outputDirectory, timeout, environment, optimization = optimization)
        assertVerdict(case.qualifiedName, expectedVerdict, outcome.report.result)

        if (expectsWitness(case, expectedVerdict)) {
            val witness = outcome.witness?.model
            assertThat(witness)
                .describedAs("verdict $expectedVerdict for ${case.qualifiedName} must carry a back-annotated witness")
                .isNotNull()
            requireNotNull(witness)
            assertThat(witness.classDeclaration)
                .describedAs("back-annotated model for ${case.qualifiedName} must reference the verified class")
                .isNotNull()
            assertThat(witness.isWitness)
                .describedAs("back-annotated InlinedOxsts for ${case.qualifiedName} must be flagged as a witness")
                .isTrue()

            val trace = outcome.report.trace
            assertThat(trace)
                .describedAs("the report of ${case.qualifiedName} must carry a trace")
                .isNotNull()
            assertThat(requireNotNull(trace).states)
                .describedAs("witness for ${case.qualifiedName} must carry state values, or it re-verifies vacuously")
                .anySatisfy {
                    assertThat(it.values).isNotEmpty()
                }

            assertWitnessFilesPersisted(outcome, case)

            if (validationPortfolio != null) {
                assertWitnessRevalidates(case, witness, validationPortfolio, outputDirectory, timeout, environment, expectedVerdict)
            }
        }
    }

    val casesExpectingWitness = CaseFilter.Matching {
        expectsWitness(it, expectedVerdictFromTags(it))
    }

    fun expectsWitness(case: VerificationCase, verdict: VerificationVerdict): Boolean {
        val property = compiledProperties.getOrPut(case.classDeclaration) {
            val compiler = SemantifyrCompiler(injector, ArtifactConfig.NONE, OptimizationConfig.NONE)
            val outputDirectory = Path("build", "test-artifacts", "witness-expectation").resolve(caseDirectoryName(case))
            compiler.compile(case.classDeclaration, outputDirectory).inlinedOxsts.property
        }
        return when (property?.expression) {
            is AG -> verdict == VerificationVerdict.Failed
            is EF -> verdict == VerificationVerdict.Passed
            else -> false
        }
    }

    private fun assertWitnessFilesPersisted(outcome: VerificationOutcome, case: VerificationCase) {
        val witnessFile = outcome.witness?.path
        val reportFile = outcome.reportPath

        assertThat(witnessFile)
            .describedAs("witness.semantifyr must be written for ${case.qualifiedName}")
            .isNotNull()
        assertThat(requireNotNull(witnessFile).fileSize())
            .describedAs("witness.semantifyr for ${case.qualifiedName} must be non-empty")
            .isGreaterThan(0)

        assertThat(reportFile.exists())
            .describedAs("report.json must be written for ${case.qualifiedName} at $reportFile")
            .isTrue()
        assertThat(reportFile.fileSize())
            .describedAs("report.json for ${case.qualifiedName} must be non-empty")
            .isGreaterThan(0)
    }

    fun <T> productAsArguments(
        cases: List<VerificationCase>,
        configurations: List<T>,
        configurationName: (T) -> String,
    ): List<Arguments> {
        return cases.flatMap { case ->
            configurations.map { configuration ->
                Arguments.of(
                    Named.of(case.qualifiedName, case),
                    Named.of(configurationName(configuration), configuration),
                )
            }
        }
    }

    suspend fun runVerificationCase(
        context: SemantifyrModelContext,
        case: VerificationCase,
        verificationPortfolio: VerificationPortfolio,
        outputDirectory: Path,
        timeout: Duration = 30.minutes,
        environment: ExecutionEnvironment = ExecutionEnvironment.Empty,
        artifacts: ArtifactConfig = ArtifactConfig.ALL,
        optimization: OptimizationConfig? = null,
    ): VerificationOutcome {
        val verifier = buildVerifier(verificationPortfolio, timeout, outputDirectory, environment, artifacts, optimization)
        return verifier.verify(case)
    }

    suspend fun checkTestModel(
        context: SemantifyrModelContext,
        case: VerificationCase,
        verificationPortfolio: VerificationPortfolio,
        outputDirectory: Path,
        validationPortfolio: VerificationPortfolio? = null,
        timeout: Duration = 1.minutes,
        validationTimeout: Duration = 1.minutes,
        environment: ExecutionEnvironment = ExecutionEnvironment.Empty,
        expectedVerdict: VerificationVerdict = expectedVerdictFromTags(case),
    ) {
        logger.info { "Checking test case $case (artifacts at $outputDirectory)" }

        assumePortfolioAvailable(verificationPortfolio, environment)
        val outcome = runVerificationCase(context, case, verificationPortfolio, outputDirectory, timeout, environment)
        val result = outcome.report.result
        assertVerdict(case.qualifiedName, expectedVerdict, result, backendDiagnostics(outputDirectory, case))
        logger.info { "Result: ${result.verdict}" }

        if (validationPortfolio == null || !expectsWitness(case, expectedVerdict)) {
            return
        }
        val witness = outcome.witness?.model
        assertThat(witness)
            .describedAs("verdict $expectedVerdict for ${case.qualifiedName} must carry a witness")
            .isNotNull()
        assertWitnessRevalidates(case, requireNotNull(witness), validationPortfolio, outputDirectory, validationTimeout, environment, expectedVerdict)
    }

    suspend fun checkWitnessReparses(
        modelPath: Path,
        qualifiedName: String,
        verificationPortfolio: VerificationPortfolio,
        outputDirectory: Path,
        timeout: Duration = 1.minutes,
        environment: ExecutionEnvironment = ExecutionEnvironment.Empty,
        validationPortfolio: VerificationPortfolio = verificationPortfolio,
    ): VerificationOutcome {
        val context = semantifyrLoader.loadStandaloneModel(modelPath)
        val case = verificationCaseDiscoverer.findByQualifiedName(context, qualifiedName)
        val outcome = runVerificationCase(context, case, verificationPortfolio, outputDirectory, timeout, environment)

        val witnessPath = outcome.witness?.path
        assertThat(witnessPath)
            .describedAs("witness.semantifyr must be written for ${case.qualifiedName}")
            .isNotNull()

        val reparsedContext = semantifyrLoader.startContext()
            .loadModel(modelPath)
            .loadModel(requireNotNull(witnessPath))
            .buildAndResolve()
        val witness = reparsedContext.modelResources.last().contents.filterIsInstance<InlinedOxsts>().single()

        val verifier = buildVerifier(validationPortfolio, timeout, outputDirectory, environment)
        val validation = verifier.verify(witness).report.result
        assertThat(validation.verdict)
            .describedAs("Reparsed witness for ${case.qualifiedName} must re-verify Passed via ${validationPortfolio.id} (${validation.message ?: "no message"})")
            .isEqualTo(VerificationVerdict.Passed)

        return outcome
    }

    private suspend fun assertWitnessRevalidates(
        case: VerificationCase,
        witness: InlinedOxsts,
        validation: VerificationPortfolio,
        outputDirectory: Path,
        timeout: Duration,
        environment: ExecutionEnvironment,
        originalVerdict: VerificationVerdict,
    ) {
        assumePortfolioAvailable(validation, environment)

        logger.info { "Validating returned witness" }

        val verifier = buildVerifier(validation, timeout, outputDirectory, environment)
        val result = verifier.verify(witness).report.result
        assertThat(result.verdict)
            .describedAs("Witness for ${case.qualifiedName} (original verdict $originalVerdict) must re-verify Passed via ${validation.id} (${result.message ?: "no message"})")
            .isEqualTo(VerificationVerdict.Passed)
    }

    private fun backendDiagnostics(outputDirectory: Path, case: VerificationCase): String {
        val caseDirectory = outputDirectory.resolve(caseDirectoryName(case)).toFile()
        if (!caseDirectory.exists()) {
            return ""
        }
        val tailLineLimit = 30
        val sections = caseDirectory.walkTopDown().filter {
            it.isFile && it.length() > 0
        }.filter {
            it.name.endsWith(".err") || it.name.endsWith(".out") || it.name.endsWith(".log")
        }.map {
            val relativePath = it.relativeTo(outputDirectory.toFile()).path
            val tail = it.readLines().takeLast(tailLineLimit).joinToString("\n")
            "--- $relativePath ---\n$tail"
        }.toList()
        if (sections.isEmpty()) {
            return ""
        }
        return "\n\nBackend diagnostics (last $tailLineLimit lines per file):\n${sections.joinToString("\n")}"
    }

    fun expectedVerdictFromTags(case: VerificationCase): VerificationVerdict {
        return when {
            "expect-pass" in case.tags -> VerificationVerdict.Passed
            "expect-fail" in case.tags -> VerificationVerdict.Failed
            else -> error(
                "Conformance case ${case.qualifiedName} must be tagged either 'expect-pass' or 'expect-fail'.",
            )
        }
    }

    companion object {
        @JvmStatic
        fun testArtifactRoot(testClass: Class<*>): Path {
            return Path("build", "test-artifacts", testClass.simpleName)
        }
    }
}
