/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalConfig
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.portfolio.LimitedConcurrencyGate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

class SmartFullPortfolioTest {

    private val gate = LimitedConcurrencyGate(4)
    private val parentInjector = mock(Injector::class.java)

    @Test
    suspend fun `racing all-passing backends produces a Passed verdict`(
        @TempDir output: Path,
    ) {
        val portfolio = SmartFullPortfolio(
            timeout = 5.seconds,
            theta = PassingBackend("theta"),
            uppaal = PassingBackend("uppaal"),
            nuxmv = PassingBackend("nuxmv"),
            spin = PassingBackend("spin"),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), gate, ExecutionEnvironment.Empty, ProgressContext.NoOp)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
    }

    @Test
    suspend fun `every backend Failed yields a Failed verdict`(
        @TempDir output: Path,
    ) {
        val portfolio = SmartFullPortfolio(
            timeout = 5.seconds,
            theta = FailingBackend("theta"),
            uppaal = FailingBackend("uppaal"),
            nuxmv = FailingBackend("nuxmv"),
            spin = FailingBackend("spin"),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), gate, ExecutionEnvironment.Empty, ProgressContext.NoOp)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
    }

    @Test
    suspend fun `no available backends yields Inconclusive with an explanatory message`(
        @TempDir output: Path,
    ) {
        val portfolio = SmartFullPortfolio(
            timeout = 5.seconds,
            theta = PassingBackend("theta", UnavailableKey),
            uppaal = PassingBackend("uppaal", UnavailableKey),
            nuxmv = PassingBackend("nuxmv", UnavailableKey),
            spin = PassingBackend("spin", UnavailableKey),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), gate, ExecutionEnvironment.Empty, ProgressContext.NoOp)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Inconclusive)
        assertThat(result.message).contains("no available backends")
    }

    @Test
    suspend fun `every backend throwing yields an Errored verdict`(
        @TempDir output: Path,
    ) {
        val portfolio = SmartFullPortfolio(
            timeout = 5.seconds,
            theta = ErroringBackend("theta"),
            uppaal = ErroringBackend("uppaal"),
            nuxmv = ErroringBackend("nuxmv"),
            spin = ErroringBackend("spin"),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), gate, ExecutionEnvironment.Empty, ProgressContext.NoOp)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Errored)
        assertThat(result.message).contains("All portfolio jobs errored")
    }

    @Test
    suspend fun `every backend NotSupported yields a NotSupported verdict`(
        @TempDir output: Path,
    ) {
        val portfolio = SmartFullPortfolio(
            timeout = 5.seconds,
            theta = NotSupportedBackend("theta"),
            uppaal = NotSupportedBackend("uppaal"),
            nuxmv = NotSupportedBackend("nuxmv"),
            spin = NotSupportedBackend("spin"),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), gate, ExecutionEnvironment.Empty, ProgressContext.NoOp)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.NotSupported)
    }

    @Test
    suspend fun `a decisive backend cancels the slower siblings`(@TempDir output: Path) {
        val slowUppaal = SlowBackend<UppaalConfig>("uppaal")
        val portfolio = SmartFullPortfolio(
            timeout = 5.seconds,
            theta = PassingBackend("theta"),
            uppaal = slowUppaal,
            nuxmv = PassingBackend("nuxmv"),
            spin = PassingBackend("spin"),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), LimitedConcurrencyGate(maxConcurrency = 8), ExecutionEnvironment.Empty, ProgressContext.NoOp)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(slowUppaal.invocations.get()).isEqualTo(2)
        assertThat(slowUppaal.cancellations.get()).isEqualTo(2)
    }
}
