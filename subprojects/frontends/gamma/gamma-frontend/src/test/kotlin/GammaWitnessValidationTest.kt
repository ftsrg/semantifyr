/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.frontends.gamma.GammaFrontend
import hu.bme.mit.semantifyr.frontends.gamma.GammaVerificationCase
import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaFrontendTestHelper
import hu.bme.mit.semantifyr.verifier.FakeBackend
import hu.bme.mit.semantifyr.verifier.FakeConfig
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.writeText

class GammaWitnessValidationTest {

    private val helper = GammaFrontendTestHelper()

    @TempDir
    private lateinit var outputDirectory: Path

    private val frontend: GammaFrontend by lazy {
        helper.buildFrontend(
            portfolio = SingleBackendPortfolio(FakeBackend.verdict("fake", VerificationVerdict.Passed), FakeConfig()),
            outputDirectory = outputDirectory,
        )
    }

    private val case: GammaVerificationCase by lazy {
        helper.discover(Path("..", "models", "examples", "Simple.gamma")).first()
    }

    @Test
    suspend fun `a witness outside the directory of its case is not read`() {
        val verificationDirectory = frontend.verify(case).outcome.reportPath.parent.toAbsolutePath()
        val outside = outputDirectory.resolve("outside.semantifyr").toAbsolutePath()
        outside.writeText("")

        for (witness in listOf(
            outside,
            verificationDirectory.resolve(verificationDirectory.relativize(outside)),
        )) {
            val exception = assertThrows<IllegalArgumentException> {
                frontend.validateWitness(case, witness)
            }
            assertThat(exception).describedAs(witness.toString()).hasMessage("The witness is not in the directory of its case")
        }
    }

    @Test
    suspend fun `a witness its case's directory does not hold is named`() {
        val missing = frontend.verify(case).outcome.reportPath.resolveSibling("missing.semantifyr")

        val exception = assertThrows<IllegalArgumentException> {
            frontend.validateWitness(case, missing)
        }

        assertThat(exception).hasMessageContaining(missing.toString())
    }
}
