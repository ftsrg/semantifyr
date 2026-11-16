/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import com.google.inject.Inject
import com.google.inject.Injector
import com.google.inject.Module
import hu.bme.mit.semantifyr.backend.BackendCapabilityChecker
import hu.bme.mit.semantifyr.backend.SoundnessDirection
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
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil
import hu.bme.mit.semantifyr.oxsts.lang.semantics.clock.ClockTestAnalysis
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.verifier.discovery.CaseFilter
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import hu.bme.mit.semantifyr.verifier.witness.WitnessValidator
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

    fun collectAcceptedCasesAsArguments(
        context: SemantifyrModelContext,
        backendModule: Module,
        capabilityChecker: Class<out BackendCapabilityChecker>,
        outputDirectory: Path,
        filter: CaseFilter = CaseFilter.All,
    ): List<Arguments> {
        val backendInjector = injector.createChildInjector(backendModule)
        val compiler = SemantifyrCompiler(injector, ArtifactConfig.NONE)
        return collectVerificationCases(context, filter).filter { case ->
            val inlinedOxsts = compiler.compile(case.classDeclaration, outputDirectory.resolve(caseDirectoryName(case))).inlinedOxsts
            val unsupported = withVerificationScopeBlocking {
                backendInjector.getInstance(capabilityChecker).check(inlinedOxsts)
            }
            if (unsupported.isNotEmpty()) {
                logger.info {
                    val refusals = unsupported.map {
                        it.description
                    }.distinct().joinToString()
                    "Leaving out ${case.qualifiedName}: ${capabilityChecker.simpleName} refuses $refusals"
                }
            }
            unsupported.isEmpty()
        }.map {
            Arguments.of(Named.of(it.qualifiedName, it))
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
            val witness = outcome.report.result.witness
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

    private fun expectsWitness(case: VerificationCase, verdict: VerificationVerdict): Boolean {
        val property = case.classDeclaration.eAllContents().asSequence().filterIsInstance<PropertyDeclaration>().firstOrNull {
            NamingUtil.getName(it) == "prop"
        } ?: return false
        return when (property.expression) {
            is AG -> verdict == VerificationVerdict.Failed
            is EF -> verdict == VerificationVerdict.Passed
            else -> false
        }
    }

    private fun assertWitnessFilesPersisted(outcome: VerificationOutcome, case: VerificationCase) {
        val witnessFile = outcome.witnessPath
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
        val result = runVerificationCase(context, case, verificationPortfolio, outputDirectory, timeout, environment).report.result
        if (result.verdict != expectedVerdict && result.soundness == SoundnessDirection.UnderApproximating && !result.isTrusted && hasUnclosedClockTest(case, outputDirectory)) {
            logger.info { "Result: ${result.verdict}, untrusted, as whole delays under-approximate a model whose clock tests are not all closed" }
            return
        }
        assertVerdict(case.qualifiedName, expectedVerdict, result, backendDiagnostics(outputDirectory, case))
        logger.info { "Result: ${result.verdict}" }

        val witness = result.witness ?: return
        val validation = validationPortfolio ?: return
        assertWitnessRevalidates(case, witness, validation, outputDirectory, validationTimeout, environment, expectedVerdict)
    }

    private fun hasUnclosedClockTest(case: VerificationCase, outputDirectory: Path): Boolean {
        val compiler = SemantifyrCompiler(injector, ArtifactConfig.NONE)
        val inlinedOxsts = compiler.compile(case.classDeclaration, outputDirectory.resolve("closedness").resolve(caseDirectoryName(case))).inlinedOxsts
        return injector.getInstance(ClockTestAnalysis::class.java).unclosedTests(inlinedOxsts.mainTransition).isNotEmpty()
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
        assertVerdict(case.qualifiedName, expectedVerdictFromTags(case), outcome.report.result)

        val witnessPath = outcome.witnessPath
        assertThat(witnessPath)
            .describedAs("witness.semantifyr must be written for ${case.qualifiedName}")
            .isNotNull()

        val reparsedContext = semantifyrLoader.startContext()
            .loadModel(modelPath)
            .loadModel(requireNotNull(witnessPath))
            .buildAndResolve()
        val witness = reparsedContext.modelResources.last().contents.filterIsInstance<InlinedOxsts>().single()

        val verifier = buildVerifier(validationPortfolio, timeout, outputDirectory, environment)
        val validation = WitnessValidator().validate(verifier, witness).outcome.report.result
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
        val result = WitnessValidator().validate(verifier, witness).outcome.report.result
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
