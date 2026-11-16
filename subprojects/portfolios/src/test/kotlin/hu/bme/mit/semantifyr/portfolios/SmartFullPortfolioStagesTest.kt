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
    fun `with more configurations than slots the stages run one after another beside the configurations listed in a row`(
        @TempDir output: Path,
    ) = runTest {
        val theta = scripted<ThetaConfig>("theta")
        val spin = scripted<SpinConfig>("spin")
        val portfolio = smart(theta = theta, spin = spin)

        val result = portfolio.verify(parentInjector, stubRequest(output, timeout = 120.seconds), LimitedConcurrencyGate(9), ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Timeout)
        assertThat(currentTime).isEqualTo(120_000)
        assertThat(startsOf(theta) + startsOf(spin)).containsExactlyInAnyOrder(
            "bounded-bmc@0s",
            "safe-dfs@0s",
            "bounded-kinduction@0s",
            "cegar-expl@0s",
            "cegar-pred-cart@10s",
            "chc@10s",
            "bounded-imc@10s",
            "cegar-expl-pred-combined@10s",
            "ic3@40s",
            "collapse@40s",
            "bitstate@40s",
        )
    }

    @Test
    fun `Spin's collapse and bitstate stay out of the last stage after its plain search ended on its own`(
        @TempDir output: Path,
    ) = runTest {
        val spin = scripted<SpinConfig>(
            "spin",
            mapOf(
                "safe-dfs" to ScriptedAnswer(1.seconds, VerificationVerdict.Inconclusive),
            ),
        )
        val portfolio = smart(theta = scripted<ThetaConfig>("theta"), spin = spin)

        val result = portfolio.verify(parentInjector, stubRequest(output, timeout = 120.seconds), LimitedConcurrencyGate(9), ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Timeout)
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
        assertThat(startsOf(spin)).containsExactlyInAnyOrder(
            "safe-dfs@0s",
            "collapse@10s",
            "bitstate@10s",
        )
    }

    @Test
    fun `the first stage ends when its own legs have ended and the second window is what is left of the third`(
        @TempDir output: Path,
    ) = runTest {
        val theta = scripted<ThetaConfig>(
            "theta",
            mapOf(
                "bounded-bmc" to ScriptedAnswer(1.seconds, VerificationVerdict.Inconclusive),
                "bounded-kinduction" to ScriptedAnswer(1.seconds, VerificationVerdict.Inconclusive),
            ),
        )
        val spin = scripted<SpinConfig>(
            "spin",
            mapOf(
                "safe-dfs" to ScriptedAnswer(1.seconds, VerificationVerdict.Inconclusive),
            ),
        )
        val portfolio = smart(theta = theta, spin = spin)

        portfolio.verify(parentInjector, stubRequest(output, timeout = 60.seconds), LimitedConcurrencyGate(9), ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(startsOf(theta)).contains(
            "cegar-pred-cart@1s",
            "bounded-imc@1s",
            "ic3@20s",
        )
    }

    @Test
    fun `the second stage is skipped when what is left of the third is under ten seconds`(
        @TempDir output: Path,
    ) = runTest {
        val theta = scripted<ThetaConfig>("theta")
        val portfolio = smart(theta = theta, spin = scripted<SpinConfig>("spin"))

        portfolio.verify(parentInjector, stubRequest(output, timeout = 45.seconds), LimitedConcurrencyGate(9), ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(startsOf(theta)).containsExactlyInAnyOrder(
            "bounded-bmc@0s",
            "bounded-kinduction@0s",
            "cegar-expl@0s",
            "cegar-pred-cart@10s",
            "chc@10s",
            "ic3@10s",
        )
    }

    @Test
    fun `on a case of 24 s the first stage runs within 8 s, the second is skipped and the last follows with the configuration listed from the second`(
        @TempDir output: Path,
    ) = runTest {
        val theta = scripted<ThetaConfig>("theta")
        val spin = scripted<SpinConfig>("spin")
        val portfolio = smart(theta = theta, spin = spin)

        val result = portfolio.verify(parentInjector, stubRequest(output, timeout = 24.seconds), LimitedConcurrencyGate(9), ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Timeout)
        assertThat(currentTime).isEqualTo(24_000)
        assertThat(startsOf(theta) + startsOf(spin)).containsExactlyInAnyOrder(
            "bounded-bmc@0s",
            "safe-dfs@0s",
            "bounded-kinduction@0s",
            "cegar-expl@0s",
            "cegar-pred-cart@8s",
            "chc@8s",
            "ic3@8s",
            "collapse@8s",
            "bitstate@8s",
        )
        assertThat(
            theta.starts.single {
                it.configuration == "cegar-pred-cart"
            }.artifactOutputPath,
        ).isEqualTo(output.resolve("last/theta-cegar-pred"))
    }

    @Test
    fun `a violation reported by Horn solving decides nothing and the other legs run on`(
        @TempDir output: Path,
    ) = runTest {
        val theta = scripted<ThetaConfig>(
            "theta",
            mapOf(
                "chc" to ScriptedAnswer(1.seconds, VerificationVerdict.Failed),
                "cegar-expl" to ScriptedAnswer(30.seconds, VerificationVerdict.Failed),
            ),
        )
        val portfolio = smart(theta = theta, spin = scripted<SpinConfig>("spin"))

        val result = portfolio.verify(parentInjector, stubRequest(output, timeout = 120.seconds), LimitedConcurrencyGate(9), ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.isTrusted).isTrue
        assertThat(currentTime).isEqualTo(30_000)
    }

    @Test
    fun `with all four backends each stage holds its configurations`(
        @TempDir output: Path,
    ) = runTest {
        val theta = scripted<ThetaConfig>("theta")
        val uppaal = scripted<UppaalConfig>("uppaal")
        val nuxmv = scripted<NuxmvConfig>("nuxmv")
        val spin = scripted<SpinConfig>("spin")
        val portfolio = smart(theta = theta, uppaal = uppaal, nuxmv = nuxmv, spin = spin)

        portfolio.verify(parentInjector, stubRequest(output, timeout = 120.seconds), LimitedConcurrencyGate(9), ExecutionEnvironment.Empty, ProgressContext.Silent)

        val starts = listOf(theta, uppaal, nuxmv, spin).flatMap { backend ->
            backend.starts.map {
                "${backend.id}:${it.configuration}@${it.at}"
            }
        }
        assertThat(starts).containsExactlyInAnyOrder(
            "theta:bounded-bmc@0s",
            "spin:safe-dfs@0s",
            "uppaal:over-approximation@0s",
            "nuxmv:ic3-invar@0s",
            "uppaal:default@0s",
            "theta:bounded-kinduction@0s",
            "nuxmv:kinduction-invar@0s",
            "theta:cegar-expl@0s",
            "nuxmv:bdd-invar@0s",
            "theta:cegar-pred-cart@10s",
            "theta:chc@10s",
            "theta:bounded-imc@10s",
            "theta:cegar-expl-pred-combined@10s",
            "uppaal:over-approximation@40s",
            "theta:ic3@40s",
            "spin:collapse@40s",
            "spin:bitstate@40s",
        )
    }

    @Test
    fun `the declared legs are every leg the stages may launch`() {
        assertThat(
            SmartFullPortfolio().legs.map {
                it.id
            },
        ).containsExactlyInAnyOrder(
            "theta:bounded-bmc",
            "theta:bounded-kinduction",
            "theta:bounded-imc",
            "theta:cegar-expl",
            "theta:cegar-pred-cart",
            "theta:cegar-expl-pred-combined",
            "theta:ic3",
            "theta:chc",
            "nuxmv:kinduction-invar",
            "nuxmv:ic3-invar",
            "nuxmv:bdd-invar",
            "spin:safe-dfs",
            "spin:collapse",
            "spin:bitstate",
            "uppaal:default",
            "uppaal:over-approximation",
        )
    }

    @Test
    fun `with as many slots as the second stage's legs and 120 s an invariant the explicit configuration proves alone within 30 s is answered`(
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

        val result = portfolio.verify(parentInjector, stubRequest(output, timeout = 120.seconds), LimitedConcurrencyGate(5), ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(result.metadata.backendId).isEqualTo("theta")
        assertThat(currentTime).isEqualTo(30_000)
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
