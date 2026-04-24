/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.VerificationResult
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.InlinedOxstsParseHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.nio.file.Files

@InjectWithOxsts
class TrivialVerdictTest {

    @Inject
    private lateinit var injector: Injector

    @Inject
    private lateinit var parseHelper: InlinedOxstsParseHelper

    @Test
    suspend fun `short-circuits to Passed when property simplifies to AG true`() {
        assertTriviallyDecided(
            model = """
                inlined oxsts of semantifyr::Anything
                init { }
                tran { }
                prop { AG true }
            """.trimIndent(),
            expected = VerificationVerdict.Passed,
        )
    }

    @Test
    suspend fun `short-circuits to Failed when property simplifies to AG false`() {
        assertTriviallyDecided(
            model = """
                inlined oxsts of semantifyr::Anything
                init { }
                tran { }
                prop { AG false }
            """.trimIndent(),
            expected = VerificationVerdict.Failed,
        )
    }

    @Test
    suspend fun `short-circuits to Passed when property simplifies to EF true`() {
        assertTriviallyDecided(
            model = """
                inlined oxsts of semantifyr::Anything
                init { }
                tran { }
                prop { EF true }
            """.trimIndent(),
            expected = VerificationVerdict.Passed,
        )
    }

    @Test
    suspend fun `short-circuits to Failed when property simplifies to EF false`() {
        assertTriviallyDecided(
            model = """
                inlined oxsts of semantifyr::Anything
                init { }
                tran { }
                prop { EF false }
            """.trimIndent(),
            expected = VerificationVerdict.Failed,
        )
    }

    @Test
    suspend fun `dispatches to portfolio when model has a variable`() {
        assertDispatchedToPortfolio(
            model = """
                inlined oxsts of semantifyr::Anything
                var x : int
                init { }
                tran { }
                prop { AG (x >= 0) }
            """.trimIndent(),
        )
    }

    @Test
    suspend fun `dispatches to portfolio when init is non-empty`() {
        assertDispatchedToPortfolio(
            model = """
                inlined oxsts of semantifyr::Anything
                init { assume(false) }
                tran { }
                prop { AG true }
            """.trimIndent(),
        )
    }

    @Test
    suspend fun `dispatches to portfolio when tran is non-empty`() {
        assertDispatchedToPortfolio(
            model = """
                inlined oxsts of semantifyr::Anything
                init { }
                tran { assume(false) }
                prop { AG true }
            """.trimIndent(),
        )
    }

    private suspend fun assertTriviallyDecided(model: String, expected: VerificationVerdict) {
        val portfolio = ErroringPortfolio()
        val result = runVerifier(model, portfolio)
        assertThat(result.verdict).isEqualTo(expected)
        assertThat(portfolio.invocations.get())
            .`as`("portfolio must not be invoked when the model is fully optimized away")
            .isEqualTo(0)
        assertThat(result.metadata.backendId).isEqualTo("trivial")
    }

    private suspend fun assertDispatchedToPortfolio(model: String) {
        val portfolio = ErroringPortfolio()
        runVerifier(model, portfolio)
        assertThat(portfolio.invocations.get())
            .`as`("portfolio must be invoked when the trivial shortcircuit does not apply")
            .isEqualTo(1)
    }

    private suspend fun runVerifier(model: String, portfolio: ErroringPortfolio): VerificationResult {
        val inlined = parseHelper.parse(model)
        return buildVerifier(portfolio).use { it.verify(inlined) }
    }

    private fun buildVerifier(portfolio: ErroringPortfolio): SemantifyrVerifier {
        return SemantifyrVerifier.builder()
            .injector(injector)
            .context(mock<SemantifyrModelContext>())
            .portfolio(portfolio)
            .artifacts(artifacts())
            .optimization(OptimizationConfig.NONE)
            .build()
    }

    private fun artifacts(): ArtifactConfig {
        return ArtifactConfig.none(Files.createTempDirectory("trivial-verdict-test-"))
    }
}
