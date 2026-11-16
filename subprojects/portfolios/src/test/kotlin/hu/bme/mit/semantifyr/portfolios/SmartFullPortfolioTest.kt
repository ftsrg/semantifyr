/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.SoundnessDirection
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalConfig
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.portfolio.LimitedConcurrencyGate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import java.nio.file.Path

class SmartFullPortfolioTest {

    private val gate = LimitedConcurrencyGate(4)
    private val parentInjector = mock(Injector::class.java)

    @Test
    suspend fun `all-passing backends produce a Passed verdict`(
        @TempDir output: Path,
    ) {
        val portfolio = SmartFullPortfolio(
            theta = PassingBackend("theta"),
            uppaal = PassingBackend("uppaal"),
            nuxmv = PassingBackend("nuxmv"),
            spin = PassingBackend("spin"),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), gate, ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
    }

    @Test
    suspend fun `every backend Failed yields a Failed verdict`(
        @TempDir output: Path,
    ) {
        val portfolio = SmartFullPortfolio(
            theta = FailingBackend("theta"),
            uppaal = FailingBackend("uppaal"),
            nuxmv = FailingBackend("nuxmv"),
            spin = FailingBackend("spin"),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), gate, ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
    }

    @Test
    suspend fun `a run whose trace cannot be read back fails and another leg decides the case`(
        @TempDir output: Path,
    ) {
        val portfolio = SmartFullPortfolio(
            theta = UnreadableTraceBackend("theta"),
            uppaal = SlowBackend("uppaal", delayMillis = 100, verdict = VerificationVerdict.Failed),
            nuxmv = SlowBackend("nuxmv", delayMillis = 100, verdict = VerificationVerdict.Failed),
            spin = SlowBackend("spin", delayMillis = 100, verdict = VerificationVerdict.Failed),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), gate, ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.metadata.backendId).isNotEqualTo("theta")
    }

    @Test
    suspend fun `a Passed invariant from an under-approximating leg does not decide the case`(
        @TempDir output: Path,
    ) {
        val portfolio = SmartFullPortfolio(
            theta = SlowBackend("theta", delayMillis = 200, verdict = VerificationVerdict.Failed),
            uppaal = NotSupportedBackend("uppaal"),
            nuxmv = NotSupportedBackend("nuxmv"),
            spin = PassingBackend("spin"),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), gate, ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.metadata.backendId).isEqualTo("theta")
    }

    @Test
    suspend fun `a nuXmv leg decides when no other leg can`(
        @TempDir output: Path,
    ) {
        val portfolio = SmartFullPortfolio(
            theta = NotSupportedBackend("theta"),
            uppaal = NotSupportedBackend("uppaal"),
            nuxmv = PassingBackend("nuxmv"),
            spin = NotSupportedBackend("spin"),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), gate, ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(result.metadata.backendId).isEqualTo("nuxmv")
    }

    @Test
    suspend fun `an under-approximating leg that reaches the target decides a reachability property`(
        @TempDir output: Path,
    ) {
        val portfolio = SmartFullPortfolio(
            theta = NotSupportedBackend("theta"),
            uppaal = NotSupportedBackend("uppaal"),
            nuxmv = NotSupportedBackend("nuxmv"),
            spin = PassingBackend("spin"),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output, OxstsFactory.createEF()), gate, ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(result.metadata.backendId).isEqualTo("spin")
    }

    @Test
    suspend fun `untrusted verdicts alone yield Inconclusive naming every leg`(
        @TempDir output: Path,
    ) {
        val portfolio = SmartFullPortfolio(
            theta = NotSupportedBackend("theta"),
            uppaal = NotSupportedBackend("uppaal"),
            nuxmv = NotSupportedBackend("nuxmv"),
            spin = PassingBackend("spin"),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), gate, ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Inconclusive)
        assertThat(result.message).contains("spin:safe-dfs=Passed (not trusted, UnderApproximating)", "uppaal:over-approximation=NotSupported", "theta:cegar-expl=NotSupported")
    }

    @Test
    suspend fun `a Passed invariant from Uppaal's over-approximation decides the case`(
        @TempDir output: Path,
    ) {
        val portfolio = SmartFullPortfolio(
            theta = NotSupportedBackend("theta"),
            uppaal = PassingBackend("uppaal"),
            nuxmv = NotSupportedBackend("nuxmv"),
            spin = PassingBackend("spin"),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), gate, ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(result.soundness).isEqualTo(SoundnessDirection.OverApproximating)
    }

    @Test
    suspend fun `no available backends yields Inconclusive with an explanatory message`(
        @TempDir output: Path,
    ) {
        val portfolio = SmartFullPortfolio(
            theta = PassingBackend("theta", UnavailableKey),
            uppaal = PassingBackend("uppaal", UnavailableKey),
            nuxmv = PassingBackend("nuxmv", UnavailableKey),
            spin = PassingBackend("spin", UnavailableKey),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), gate, ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Inconclusive)
        assertThat(result.message).contains("No backend is installed")
    }

    @Test
    suspend fun `every backend throwing yields an Errored verdict`(
        @TempDir output: Path,
    ) {
        val portfolio = SmartFullPortfolio(
            theta = ErroringBackend("theta"),
            uppaal = ErroringBackend("uppaal"),
            nuxmv = ErroringBackend("nuxmv"),
            spin = ErroringBackend("spin"),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), gate, ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Errored)
        assertThat(result.message).contains("All portfolio jobs errored")
    }

    @Test
    suspend fun `every backend NotSupported yields a NotSupported verdict`(
        @TempDir output: Path,
    ) {
        val portfolio = SmartFullPortfolio(
            theta = NotSupportedBackend("theta"),
            uppaal = NotSupportedBackend("uppaal"),
            nuxmv = NotSupportedBackend("nuxmv"),
            spin = NotSupportedBackend("spin"),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), gate, ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.NotSupported)
    }

    @Test
    suspend fun `a trusted answer cancels the legs still running`(@TempDir output: Path) {
        val slowUppaal = SlowBackend<UppaalConfig>("uppaal")
        val portfolio = SmartFullPortfolio(
            theta = PassingBackend("theta"),
            uppaal = slowUppaal,
            nuxmv = PassingBackend("nuxmv"),
            spin = PassingBackend("spin"),
        )

        val result = portfolio.verify(parentInjector, stubRequest(output), LimitedConcurrencyGate(maxConcurrency = 16), ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(slowUppaal.invocations.get()).isEqualTo(2)
        assertThat(slowUppaal.cancellations.get()).isEqualTo(2)
    }
}
