/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendConfig
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvConfig
import hu.bme.mit.semantifyr.backends.spin.verification.SpinConfig
import hu.bme.mit.semantifyr.backends.theta.ThetaConfig
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalConfig
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.portfolio.LimitedConcurrencyGate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SmartFullPortfolioStagesTest {

    private val parentInjector = mock(Injector::class.java)

    @Test
    fun `with no more available configurations than slots every one starts at once without a window`(
        @TempDir output: Path,
    ) = runTest {
        val theta = scripted<ThetaConfig>(
            "theta",
            mapOf(
                "cegar-pred-cart" to ScriptedAnswer(50.seconds, VerificationVerdict.Passed),
            ),
        )
        val portfolio = smart(theta = theta)

        val result = portfolio.verify(parentInjector, stubRequest(output, timeout = 120.seconds), LimitedConcurrencyGate(7), ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(currentTime).isEqualTo(50_000)
        assertThat(theta.starts).hasSize(7).allMatch {
            it.at == Duration.ZERO
        }
    }

    @Test
    fun `with more configurations than slots the stages run in order, each cut at its window`(
        @TempDir output: Path,
    ) = runTest {
        val theta = scripted<ThetaConfig>("theta")
        val spin = scripted<SpinConfig>("spin")
        val messages = mutableListOf<String>()
        val progress = object : ProgressContext {
            override fun checkIsCancelled() {}
            override fun reportProgress(message: String) {
                messages += message
            }
        }
        val portfolio = smart(theta = theta, spin = spin)

        val result = portfolio.verify(parentInjector, stubRequest(output, timeout = 120.seconds), LimitedConcurrencyGate(2), ExecutionEnvironment.Empty, progress)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Timeout)
        assertThat(currentTime).isEqualTo(120_000)
        assertThat(startsOf(theta)).containsExactly(
            "bounded-bmc@0s",
            "cegar-expl@10s",
            "cegar-pred-cart@10s",
            "cegar-expl@40s",
            "cegar-pred-cart@40s",
        )
        assertThat(startsOf(spin)).containsExactly(
            "safe-dfs@0s",
        )
        assertThat(result.message).contains(
            "first stage: theta:bounded-bmc cut, spin:safe-dfs cut, theta:bounded-kinduction never started, theta:cegar-expl never started",
            "second stage: theta:cegar-expl cut, theta:cegar-pred-cart cut, theta:bounded-imc never started, theta:cegar-expl-pred-combined never started",
            "last stage: theta:cegar-expl cut, theta:cegar-pred-cart cut, theta:ic3 never started, spin:collapse never started, spin:bitstate never started",
        )
        assertThat(messages).contains(
            "first stage: racing 4 backend configurations within 10s",
            "second stage: racing 4 backend configurations within 30s",
            "last stage: racing 5 backend configurations until the case's time runs out",
        )
    }

    @Test
    fun `a configuration run in two stages keeps the artifacts of each run`(
        @TempDir output: Path,
    ) = runTest {
        val theta = scripted<ThetaConfig>("theta")
        val portfolio = smart(theta = theta, spin = scripted<SpinConfig>("spin"))

        portfolio.verify(parentInjector, stubRequest(output, timeout = 120.seconds), LimitedConcurrencyGate(2), ExecutionEnvironment.Empty, ProgressContext.Silent)

        val explicitRuns = theta.starts.filter {
            it.configuration == "cegar-expl"
        }.map {
            it.artifactOutputPath
        }
        assertThat(explicitRuns).containsExactly(
            output.resolve("second/theta-cegar-expl"),
            output.resolve("last/theta-cegar-expl"),
        )
    }

    @Test
    fun `a configuration that ended on its own is not started again and Spin's variants stay out after its plain search ended`(
        @TempDir output: Path,
    ) = runTest {
        val theta = scripted<ThetaConfig>(
            "theta",
            mapOf(
                "bounded-kinduction" to ScriptedAnswer(1.seconds, VerificationVerdict.Inconclusive),
                "cegar-expl" to ScriptedAnswer(1.seconds, VerificationVerdict.Inconclusive),
            ),
        )
        val spin = scripted<SpinConfig>(
            "spin",
            mapOf(
                "safe-dfs" to ScriptedAnswer(1.seconds, VerificationVerdict.Inconclusive),
            ),
        )
        val portfolio = smart(theta = theta, spin = spin)

        val result = portfolio.verify(parentInjector, stubRequest(output, timeout = 120.seconds), LimitedConcurrencyGate(2), ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Timeout)
        assertThat(startsOf(theta)).containsExactly(
            "bounded-bmc@0s",
            "bounded-kinduction@1s",
            "cegar-expl@2s",
            "cegar-pred-cart@10s",
            "bounded-imc@10s",
            "cegar-pred-cart@40s",
            "ic3@40s",
        )
        assertThat(startsOf(spin)).containsExactly(
            "safe-dfs@0s",
        )
    }

    @Test
    fun `Spin's collapse and bitstate start in the last stage when its plain search was cut in the first`(
        @TempDir output: Path,
    ) = runTest {
        val spin = scripted<SpinConfig>(
            "spin",
            mapOf(
                "collapse" to ScriptedAnswer(5.seconds, VerificationVerdict.Failed),
            ),
        )
        val portfolio = smart(spin = spin)

        val result = portfolio.verify(parentInjector, stubRequest(output, timeout = 120.seconds), LimitedConcurrencyGate(2), ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(currentTime).isEqualTo(15_000)
        assertThat(startsOf(spin)).containsExactly(
            "safe-dfs@0s",
            "collapse@10s",
            "bitstate@10s",
        )
    }

    @Test
    fun `the second window is what is left of the third when it starts`(
        @TempDir output: Path,
    ) = runTest {
        val theta = scripted<ThetaConfig>(
            "theta",
            mapOf(
                "bounded-bmc" to ScriptedAnswer(1.seconds, VerificationVerdict.Inconclusive),
                "bounded-kinduction" to ScriptedAnswer(1.seconds, VerificationVerdict.Inconclusive),
                "cegar-expl" to ScriptedAnswer(1.seconds, VerificationVerdict.Inconclusive),
            ),
        )
        val spin = scripted<SpinConfig>(
            "spin",
            mapOf(
                "safe-dfs" to ScriptedAnswer(1.seconds, VerificationVerdict.Inconclusive),
            ),
        )
        val portfolio = smart(theta = theta, spin = spin)

        portfolio.verify(parentInjector, stubRequest(output, timeout = 60.seconds), LimitedConcurrencyGate(2), ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(startsOf(theta)).containsSubsequence(
            "cegar-pred-cart@2s",
            "bounded-imc@2s",
            "cegar-pred-cart@20s",
        )
    }

    @Test
    fun `on a short case a stage whose window would be under ten seconds is skipped`(
        @TempDir output: Path,
    ) = runTest {
        val theta = scripted<ThetaConfig>("theta")
        val portfolio = smart(theta = theta, spin = scripted<SpinConfig>("spin"))

        portfolio.verify(parentInjector, stubRequest(output, timeout = 45.seconds), LimitedConcurrencyGate(2), ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(startsOf(theta)).containsExactly(
            "bounded-bmc@0s",
            "cegar-expl@10s",
            "cegar-pred-cart@10s",
        )
    }

    @Test
    fun `on a case shorter than three times the shortest window only the last stage runs`(
        @TempDir output: Path,
    ) = runTest {
        val theta = scripted<ThetaConfig>("theta")
        val spin = scripted<SpinConfig>("spin")
        val portfolio = smart(theta = theta, spin = spin)

        val result = portfolio.verify(parentInjector, stubRequest(output, timeout = 25.seconds), LimitedConcurrencyGate(2), ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Timeout)
        assertThat(currentTime).isEqualTo(25_000)
        assertThat(startsOf(theta)).containsExactly(
            "cegar-expl@0s",
            "cegar-pred-cart@0s",
        )
        assertThat(spin.starts).isEmpty()
    }

    @Test
    fun `with Theta and Spin on two slots and 120 s an invariant the explicit configuration proves alone within 30 s is answered`(
        @TempDir output: Path,
    ) = runTest {
        val theta = scripted<ThetaConfig>(
            "theta",
            mapOf(
                "cegar-expl" to ScriptedAnswer(30.seconds, VerificationVerdict.Passed),
            ),
        )
        val spin = scripted<SpinConfig>(
            "spin",
            mapOf(
                "safe-dfs" to ScriptedAnswer(2.seconds, VerificationVerdict.Passed),
            ),
        )
        val portfolio = smart(theta = theta, spin = spin)

        val result = portfolio.verify(parentInjector, stubRequest(output, timeout = 120.seconds), LimitedConcurrencyGate(2), ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(result.metadata.backendId).isEqualTo("theta")
    }

    private fun startsOf(backend: ScriptedBackend<*>): List<String> {
        return backend.starts.map {
            "${it.configuration}@${it.at}"
        }
    }

    private fun <T : BackendConfig> TestScope.scripted(
        id: String,
        answers: Map<String, ScriptedAnswer> = emptyMap(),
    ): ScriptedBackend<T> {
        return ScriptedBackend(
            id,
            {
                testScheduler.currentTime.milliseconds
            },
            answers,
        )
    }

    private fun TestScope.smart(
        theta: ScriptedBackend<ThetaConfig> = ScriptedBackend("theta", ::noTime, executorKey = UnavailableKey),
        uppaal: ScriptedBackend<UppaalConfig> = ScriptedBackend("uppaal", ::noTime, executorKey = UnavailableKey),
        nuxmv: ScriptedBackend<NuxmvConfig> = ScriptedBackend("nuxmv", ::noTime, executorKey = UnavailableKey),
        spin: ScriptedBackend<SpinConfig> = ScriptedBackend("spin", ::noTime, executorKey = UnavailableKey),
    ): SmartFullPortfolio {
        return SmartFullPortfolio(
            theta = theta,
            uppaal = uppaal,
            nuxmv = nuxmv,
            spin = spin,
            timeSource = testScheduler.timeSource,
        )
    }

    private fun noTime(): Duration {
        return Duration.ZERO
    }
}
