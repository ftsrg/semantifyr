/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.portfolio.LimitedConcurrencyGate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

class ThetaFullPortfolioTest {

    private val gate = LimitedConcurrencyGate(4)
    private val parentInjector = mock(Injector::class.java)

    @Test
    suspend fun `racing all-passing Theta configurations produces a Passed verdict`(
        @TempDir output: Path,
    ) {
        val portfolio = ThetaFullPortfolio(
            5.seconds,
            PassingBackend("theta"),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), gate, ExecutionEnvironment.Empty, ProgressContext.NoOp)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
    }

    @Test
    suspend fun `unavailable Theta executor yields Inconclusive with an explanatory message`(
        @TempDir output: Path,
    ) {
        val portfolio = ThetaFullPortfolio(
            5.seconds,
            PassingBackend("theta", UnavailableKey),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), gate, ExecutionEnvironment.Empty, ProgressContext.NoOp)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Inconclusive)
        assertThat(result.message).contains("no available Theta executor")
    }
}
