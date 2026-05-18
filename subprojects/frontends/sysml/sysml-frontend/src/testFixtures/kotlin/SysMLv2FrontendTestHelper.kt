/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.testing

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.frontends.sysml.SysMLv2Frontend
import hu.bme.mit.semantifyr.frontends.sysml.SysMLv2Variant
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.VerificationResult
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.provider.Arguments
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.Path
import kotlin.streams.asStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class SysMLv2FrontendTestHelper {

    private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
    private val loader = injector.getInstance(SemantifyrLoader::class.java)
    private val verifierTestHelper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

    fun buildFrontend(
        sourcePath: Path,
        portfolio: VerificationPortfolio,
        outputDirectory: Path,
        variant: SysMLv2Variant = SysMLv2Variant.Default,
        timeout: Duration = 2.minutes,
        artifacts: ArtifactConfig = ArtifactConfig.ALL,
    ): SysMLv2Frontend {
        return SysMLv2Frontend.builder()
            .source(sourcePath)
            .variant(variant)
            .injector(injector)
            .loader(loader)
            .portfolio(portfolio)
            .timeout(timeout)
            .artifacts(artifacts)
            .outputDirectory(outputDirectory)
            .build()
    }

    fun discoverAsArguments(
        sourcePath: Path,
        portfolio: VerificationPortfolio,
        outputDirectory: Path,
        variants: List<SysMLv2Variant> = listOf(SysMLv2Variant.Default),
    ): Stream<Arguments> {
        return variants.asSequence().flatMap {
            val frontend = buildFrontend(sourcePath, portfolio, outputDirectory, it)
            frontend.verificationCases().asSequence().map { case ->
                Arguments.of(
                    Named.of(it.name.lowercase(), it),
                    Named.of(case.qualifiedName, case),
                )
            }
        }.asStream()
    }

    suspend fun checkVerificationCase(
        frontend: SysMLv2Frontend,
        case: VerificationCase,
        expectedVerdict: VerificationVerdict = VerificationVerdict.Passed,
    ): VerificationResult {
        val matchedCase = frontend.verificationCases().single {
            it.qualifiedName == case.qualifiedName
        }
        val result = frontend.verify(matchedCase)
        Assertions.assertEquals(
            expectedVerdict,
            result.verdict,
            "Expected ${case.qualifiedName} verdict $expectedVerdict, got ${result.verdict} (${result.message ?: "no message"})",
        )
        return result
    }

    suspend fun checkConformance(
        frontend: SysMLv2Frontend,
        case: VerificationCase,
        validationPortfolio: VerificationPortfolio? = null,
        expectedVerdict: VerificationVerdict = VerificationVerdict.Passed,
    ) {
        val matchedCase = frontend.verificationCases().single {
            it.qualifiedName == case.qualifiedName
        }
        verifierTestHelper.checkTestModel(
            context = frontend.modelContext,
            case = matchedCase,
            verificationPortfolio = frontend.portfolio,
            outputDirectory = frontend.outputDirectory,
            validationPortfolio = validationPortfolio,
            timeout = frontend.timeout ?: 30.minutes,
            expectedVerdict = expectedVerdict,
        )
    }

    companion object {
        val MODELS_ROOT: Path = Path("build", "test-models")

        fun testArtifactRoot(testClass: Class<*>): Path {
            return Path("build", "test-artifacts", testClass.simpleName)
        }

        fun modelPath(name: String): Path {
            return MODELS_ROOT.resolve(name)
        }
    }
}
