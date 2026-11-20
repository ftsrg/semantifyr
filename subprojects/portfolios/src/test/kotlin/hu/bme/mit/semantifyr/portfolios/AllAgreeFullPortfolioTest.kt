/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.portfolio.LimitedConcurrencyGate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import java.nio.file.Path

class AllAgreeFullPortfolioTest {

    private val gate = LimitedConcurrencyGate(4)
    private val parentInjector = mock(Injector::class.java)

    @Test
    suspend fun `trusted backends that disagree yield Errored naming them`(
        @TempDir output: Path,
    ) {
        val portfolio = AllAgreeFullPortfolio(
            theta = PassingBackend("theta"),
            nuxmv = PassingBackend("nuxmv"),
            uppaal = PassingBackend("uppaal"),
            spin = FailingBackend("spin"),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), gate, ExecutionEnvironment.Empty, ProgressContext.Silent, noReadBack)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Errored)
        assertThat(result.message).contains("theta:cegar-expl-pred-combined=Passed (Exact)", "spin:safe-dfs=Failed (UnderApproximating)")
    }

    @Test
    suspend fun `a disagreement explained by the directions resolves to the trusted side`(
        @TempDir output: Path,
    ) {
        val portfolio = AllAgreeFullPortfolio(
            theta = FailingBackend("theta"),
            nuxmv = FailingBackend("nuxmv"),
            uppaal = PassingBackend("uppaal"),
            spin = PassingBackend("spin"),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), gate, ExecutionEnvironment.Empty, ProgressContext.Silent, noReadBack)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.message).contains("uppaal:default=Passed (not trusted, UnderApproximating)", "spin:safe-dfs=Passed (not trusted, UnderApproximating)")
    }

    @Test
    suspend fun `every backend passing a reachability case passes with no message`(
        @TempDir output: Path,
    ) {
        val portfolio = AllAgreeFullPortfolio(
            theta = PassingBackend("theta"),
            nuxmv = PassingBackend("nuxmv"),
            uppaal = PassingBackend("uppaal"),
            spin = PassingBackend("spin"),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output, OxstsFactory.createEF()), gate, ExecutionEnvironment.Empty, ProgressContext.Silent, noReadBack)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(result.message).isNull()
    }

    @Test
    suspend fun `no available backends yields Inconclusive with an explanatory message`(
        @TempDir output: Path,
    ) {
        val portfolio = AllAgreeFullPortfolio(
            theta = PassingBackend("theta", UnavailableKey),
            nuxmv = PassingBackend("nuxmv", UnavailableKey),
            uppaal = PassingBackend("uppaal", UnavailableKey),
            spin = PassingBackend("spin", OtherUnavailableKey),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), gate, ExecutionEnvironment.Empty, ProgressContext.Silent, noReadBack)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Inconclusive)
        assertThat(result.message).contains("No backend is installed")
    }
}
