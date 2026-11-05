/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backends.theta.ThetaConfig
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.portfolio.LimitedConcurrencyGate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

class ThetaFullPortfolioTest {

    private val gate = LimitedConcurrencyGate(4)
    private val parentInjector = mock(Injector::class.java)

    @Test
    suspend fun `racing all-passing Theta configurations produces a Passed verdict`(
        @TempDir output: Path,
    ) {
        val portfolio = ThetaFullPortfolio(
            PassingBackend("theta"),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), gate, ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
    }

    @Test
    suspend fun `the request's timeout bounds every racing leg`(
        @TempDir output: Path,
    ) {
        val theta = SlowBackend<ThetaConfig>("theta")
        val portfolio = ThetaFullPortfolio(theta)

        val result = portfolio.verify(parentInjector, stubRequest(output, timeout = 200.milliseconds), gate, ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Timeout)
        assertThat(result.message).contains("no trusted verdict within 200ms")
        assertThat(theta.cancellations.get()).isPositive
    }

    @Test
    suspend fun `unavailable Theta executor yields Inconclusive with an explanatory message`(
        @TempDir output: Path,
    ) {
        val portfolio = ThetaFullPortfolio(
            PassingBackend("theta", UnavailableKey),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), gate, ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Inconclusive)
        assertThat(result.message).contains("no available Theta executor")
    }
}
