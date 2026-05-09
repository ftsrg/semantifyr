/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.verifier.discovery.CaseFilter
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import hu.bme.mit.semantifyr.verifier.witness.WitnessValidator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
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
    ): SemantifyrVerifier {
        outputDirectory.createDirectories()
        return SemantifyrVerifier.builder()
            .portfolio(verificationPortfolio)
            .environment(environment)
            .artifacts(artifacts)
            .outputDirectory(outputDirectory)
            .timeout(timeout)
            .build()
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

    suspend fun checkVerificationCase(
        context: SemantifyrModelContext,
        case: VerificationCase,
        verificationPortfolio: VerificationPortfolio,
        outputDirectory: Path,
        timeout: Duration = 30.minutes,
        environment: ExecutionEnvironment = ExecutionEnvironment.Empty,
        expectedVerdict: VerificationVerdict = VerificationVerdict.Passed,
    ) {
        val result = runVerificationCase(context, case, verificationPortfolio, outputDirectory, timeout, environment)
        Assertions.assertEquals(
            expectedVerdict,
            result.verdict,
            "Expected ${case.qualifiedName} verdict $expectedVerdict, got ${result.verdict} (${result.message ?: "no message"})",
        )

        if (expectsWitness(case, expectedVerdict)) {
            assertThat(result.trace)
                .describedAs("verdict $expectedVerdict for ${case.qualifiedName} must carry a Trace")
                .isNotNull()
            val trace = requireNotNull(result.trace)
            assertThat(trace.backAnnotatedModel.classDeclaration)
                .describedAs("back-annotated model for ${case.qualifiedName} must reference the verified class")
                .isNotNull()
            assertThat(trace.backAnnotatedModel.isWitness)
                .describedAs("back-annotated InlinedOxsts for ${case.qualifiedName} must be flagged as a witness")
                .isTrue()

            assertWitnessFilesPersisted(outputDirectory, case)
        }
    }

    private fun expectsWitness(case: VerificationCase, verdict: VerificationVerdict): Boolean {
        val prop = case.classDeclaration.eAllContents().asSequence().filterIsInstance<PropertyDeclaration>().firstOrNull {
            it.name == "prop"
        } ?: return false
        return when (prop.expression) {
            is AG -> verdict == VerificationVerdict.Failed
            is EF -> verdict == VerificationVerdict.Passed
            else -> false
        }
    }

    private fun assertWitnessFilesPersisted(artifactsRoot: Path, case: VerificationCase) {
        val caseDir = artifactsRoot.resolve(qualifiedNameToDirectoryName(case.qualifiedName))
        val witnessFile = caseDir.resolve("witness.oxsts")
        val traceFile = caseDir.resolve("trace.json")

        assertThat(witnessFile.exists())
            .describedAs("witness.oxsts must be written for ${case.qualifiedName} at $witnessFile")
            .isTrue()
        assertThat(witnessFile.fileSize())
            .describedAs("witness.oxsts for ${case.qualifiedName} must be non-empty")
            .isGreaterThan(0)

        assertThat(traceFile.exists())
            .describedAs("trace.json must be written for ${case.qualifiedName} at $traceFile")
            .isTrue()
        assertThat(traceFile.fileSize())
            .describedAs("trace.json for ${case.qualifiedName} must be non-empty")
            .isGreaterThan(0)
    }

    fun <T> productAsArguments(
        cases: List<VerificationCase>,
        configs: List<T>,
        configName: (T) -> String,
    ): List<Arguments> {
        return cases.flatMap { case ->
            configs.map { config ->
                Arguments.of(
                    Named.of(case.qualifiedName, case),
                    Named.of(configName(config), config),
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
    ): VerificationResult {
        val verifier = buildVerifier(verificationPortfolio, timeout, outputDirectory, environment, artifacts)
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

        val result = runVerificationCase(context, case, verificationPortfolio, outputDirectory, timeout, environment)
        Assumptions.assumeFalse(
            result.verdict == VerificationVerdict.NotSupported,
            "Portfolio ${verificationPortfolio.id} reports ${case.qualifiedName} is not supported: ${result.message ?: "no message"}",
        )
        Assumptions.assumeFalse(
            result.verdict == VerificationVerdict.Inconclusive,
            "Portfolio ${verificationPortfolio.id} could not decide ${case.qualifiedName} within $timeout: ${result.message ?: "no message"}",
        )
        Assertions.assertTrue(
            result.verdict.isDecisive,
        ) {
            "Portfolio ${verificationPortfolio.id} did not produce a decisive verdict for ${case.qualifiedName} (got ${result.verdict}: ${result.message ?: "no message"}).${
                backendDiagnostics(
                    outputDirectory,
                    case,
                )
            }"
        }
        Assertions.assertEquals(
            expectedVerdict,
            result.verdict,
        ) {
            "Expected ${case.qualifiedName} verdict $expectedVerdict, got ${result.verdict} " +
                "(${result.message ?: "no message"}).${backendDiagnostics(outputDirectory, case)}"
        }

        logger.info { "Result: ${result.verdict}" }

        val trace = result.trace ?: return
        val validation = validationPortfolio ?: return
        val report = validation.availability(environment)
        Assumptions.assumeTrue(
            report is AvailabilityReport.Available,
            "Validation portfolio ${validation.id} is unavailable on this host. Skipping witness validation for ${case.qualifiedName}",
        )

        logger.info { "Validating returned witness" }

        val validationDirectory = outputDirectory.resolve(qualifiedNameToDirectoryName(case.qualifiedName)).resolve("validation")
        val verifier = buildVerifier(validation, validationTimeout, validationDirectory, environment)
        val outcome = WitnessValidator().validate(verifier, trace)
        assertThat(outcome.isValid)
            .describedAs("Witness for ${case.qualifiedName} (original verdict $expectedVerdict) must re-verify Passed via ${validation.id} but got ${outcome.verification.verdict} (${outcome.verification.message ?: "no message"})")
            .isTrue()
    }

    private fun backendDiagnostics(outputDirectory: Path, case: VerificationCase): String {
        val caseDir = outputDirectory.resolve(qualifiedNameToDirectoryName(case.qualifiedName)).toFile()
        if (!caseDir.exists()) {
            return ""
        }
        val tailLineLimit = 30
        val sections = caseDir.walkTopDown().filter {
            it.isFile && it.length() > 0
        }.filter {
            it.name.endsWith(".err") || it.name.endsWith(".out") || it.name.endsWith(".log")
        }.map {
            val rel = it.relativeTo(outputDirectory.toFile()).path
            val tail = it.readLines().takeLast(tailLineLimit).joinToString("\n")
            "--- $rel ---\n$tail"
        }.toList()
        if (sections.isEmpty()) {
            return ""
        }
        return "\n\nBackend diagnostics (last $tailLineLimit lines per file):\n${sections.joinToString("\n")}"
    }

    private fun expectedVerdictFromTags(case: VerificationCase): VerificationVerdict {
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
