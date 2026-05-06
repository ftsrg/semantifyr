/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.testing

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.frontends.gamma.GammaFrontend
import hu.bme.mit.semantifyr.frontends.gamma.GammaVariant
import hu.bme.mit.semantifyr.frontends.gamma.GammaVerificationCase
import hu.bme.mit.semantifyr.frontends.gamma.GammaVerificationResult
import hu.bme.mit.semantifyr.frontends.gamma.discovery.GammaVerificationCaseDiscoverer
import hu.bme.mit.semantifyr.frontends.gamma.lang.GammaStandaloneSetup
import hu.bme.mit.semantifyr.frontends.gamma.reader.GammaReader
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import hu.bme.mit.semantifyr.verifier.witness.WitnessValidator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.provider.Arguments
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.Path
import kotlin.streams.asStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class GammaFrontendTestHelper {

    private val gammaInjector = GammaStandaloneSetup().createInjectorAndDoEMFRegistration()
    private val oxstsInjector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
    private val gammaReader = gammaInjector.getInstance(GammaReader::class.java)
    private val gammaDiscoverer = gammaInjector.getInstance(GammaVerificationCaseDiscoverer::class.java)

    fun discover(sourcePath: Path): List<GammaVerificationCase> {
        val gammaModel = gammaReader.readGammaFile(sourcePath.toFile())
        return gammaDiscoverer.discover(gammaModel)
    }

    fun discoverAsArguments(sourcePath: Path): Stream<Arguments> {
        return discover(sourcePath).asSequence().map {
            Arguments.of(Named.of(it.qualifiedName, it))
        }.asStream()
    }

    fun buildFrontend(
        portfolio: VerificationPortfolio,
        outputDirectory: Path,
        variant: GammaVariant = GammaVariant.Default,
        timeout: Duration = 30.minutes,
        artifacts: ArtifactConfig = ArtifactConfig.ALL,
    ): GammaFrontend {
        return GammaFrontend.builder()
            .variant(variant)
            .gammaInjector(gammaInjector)
            .oxstsInjector(oxstsInjector)
            .portfolio(portfolio)
            .timeout(timeout)
            .artifacts(artifacts)
            .outputDirectory(outputDirectory)
            .build()
    }

    suspend fun checkVerificationCase(
        frontend: GammaFrontend,
        gammaVerificationCase: GammaVerificationCase,
        expectedVerdict: VerificationVerdict = VerificationVerdict.Passed,
    ): GammaVerificationResult {
        val result = frontend.verify(gammaVerificationCase)
        Assertions.assertEquals(
            expectedVerdict,
            result.verification.verdict,
            "Expected ${gammaVerificationCase.qualifiedName} verdict $expectedVerdict, got ${result.verification.verdict} (${result.verification.message ?: "no message"})",
        )
        return result
    }

    suspend fun checkConformance(
        frontend: GammaFrontend,
        gammaVerificationCase: GammaVerificationCase,
        validationPortfolio: VerificationPortfolio,
        expectedVerdict: VerificationVerdict = VerificationVerdict.Passed,
    ): GammaVerificationResult {
        val result = checkVerificationCase(frontend, gammaVerificationCase, expectedVerdict)
        val witness = result.witness ?: return result

        val validationDirectory = frontend.outputDirectory
            .resolve(gammaVerificationCase.qualifiedName)
            .resolve("validation")
        val validationVerifier = frontend.buildVerifierWith(validationPortfolio, validationDirectory)
        val outcome = WitnessValidator().validate(validationVerifier, witness.trace)
        assertThat(outcome.isValid)
            .describedAs(
                "Witness for ${gammaVerificationCase.qualifiedName} must re-verify Passed via ${validationPortfolio.id} but got ${outcome.verification.verdict} (${outcome.verification.message ?: "no message"})",
            )
            .isTrue()
        return result
    }

    companion object {
        @JvmStatic
        fun testArtifactRoot(testClass: Class<*>): Path {
            return Path("build", "test-artifacts", testClass.simpleName)
        }
    }
}
