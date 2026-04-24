/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.VerificationCase
import hu.bme.mit.semantifyr.backend.VerificationResult
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.verification.discovery.CaseFilter
import hu.bme.mit.semantifyr.verification.discovery.VerificationCaseDiscoverer
import hu.bme.mit.semantifyr.verification.portfolio.VerificationPortfolio
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.provider.Arguments
import java.nio.file.Files
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class SemantifyrVerifierTestHelper @Inject constructor(
    val semantifyrLoader: SemantifyrLoader,
    val verificationCaseDiscoverer: VerificationCaseDiscoverer,
) {

    private fun buildVerifier(
        context: SemantifyrModelContext,
        verificationPortfolio: VerificationPortfolio,
        timeout: Duration,
        environment: ExecutionEnvironment = ExecutionEnvironment.Empty,
        artifacts: ArtifactConfig = tempArtifactConfig(),
    ): SemantifyrVerifier {
        return SemantifyrVerifier.builder()
            .context(context)
            .portfolio(verificationPortfolio)
            .environment(environment)
            .artifacts(artifacts)
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

    private fun tempArtifactConfig(): ArtifactConfig {
        return ArtifactConfig.none(Files.createTempDirectory("semantifyr-test-artifacts-"))
    }

    suspend fun checkVerificationCase(
        context: SemantifyrModelContext,
        case: VerificationCase,
        verificationPortfolio: VerificationPortfolio,
        timeout: Duration = 30.minutes,
        environment: ExecutionEnvironment = ExecutionEnvironment.Empty,
        expectedVerdict: VerificationVerdict = VerificationVerdict.Passed,
    ) {
        val result = runVerificationCase(context, case, verificationPortfolio, timeout, environment)
        Assertions.assertEquals(
            expectedVerdict,
            result.verdict,
            "Expected ${case.qualifiedName} verdict $expectedVerdict, got ${result.verdict} (${result.message ?: "no message"})",
        )
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
        timeout: Duration = 30.minutes,
        environment: ExecutionEnvironment = ExecutionEnvironment.Empty,
    ): VerificationResult {
        return buildVerifier(context, verificationPortfolio, timeout, environment).use {
            it.verify(case)
        }
    }

}
