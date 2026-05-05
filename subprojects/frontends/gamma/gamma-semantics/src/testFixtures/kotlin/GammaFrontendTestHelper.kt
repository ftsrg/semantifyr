/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.semantics.testing

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

    // TODO: Re-enable once GammaFrontend.validateWitness is restored (see comment there).
    // suspend fun checkValidWitness(
    //     frontend: GammaFrontend,
    //     gammaVerificationCase: GammaVerificationCase,
    //     verificationResult: GammaVerificationResult,
    // ): WitnessValidationResult {
    //     val witness = requireNotNull(verificationResult.witness) {
    //         "Expected ${gammaVerificationCase.qualifiedName} to produce a witness, got verdict ${verificationResult.verification.verdict}"
    //     }
    //     val validationResult = frontend.validateWitness(gammaVerificationCase, witness)
    //     Assertions.assertTrue(
    //         validationResult.isValid,
    //         "Witness for ${gammaVerificationCase.qualifiedName} did not validate: $validationResult (${validationResult.verification.message ?: "no message"})",
    //     )
    //     return validationResult
    // }

    companion object {
        @JvmStatic
        fun testArtifactRoot(testClass: Class<*>): Path {
            return Path("build", "test-artifacts", testClass.simpleName)
        }
    }
}
