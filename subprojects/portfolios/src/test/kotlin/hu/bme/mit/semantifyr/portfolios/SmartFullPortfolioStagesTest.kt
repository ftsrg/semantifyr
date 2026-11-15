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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
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
    fun `with more configurations than slots the stages run one after another, each cut at its window`(
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

        val result = portfolio.verify(parentInjector, stubRequest(output, timeout = 120.seconds), LimitedConcurrencyGate(9), ExecutionEnvironment.Empty, progress)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Timeout)
        assertThat(currentTime).isEqualTo(120_000)
        assertThat(startsOf(theta) + startsOf(spin)).containsExactlyInAnyOrder(
            "bounded-bmc@0s",
            "safe-dfs@0s",
            "bounded-kinduction@0s",
            "cegar-expl@0s",
            "cegar-expl@10s",
            "cegar-pred-cart@10s",
            "bounded-imc@10s",
            "cegar-expl-pred-combined@10s",
            "cegar-expl@40s",
            "cegar-pred-cart@40s",
            "ic3@40s",
            "collapse@40s",
            "bitstate@40s",
        )
        assertThat(result.message).contains(
            "first stage: ",
            "second stage: ",
            "last stage: ",
            "theta:cegar-expl-pred-combined cut",
        ).doesNotContain("never started")
        assertThat(messages).contains(
            "first stage: racing 4 backend configurations within 10s",
            "second stage: racing 4 backend configurations within 30s",
            "last stage: racing 5 backend configurations until the case's time runs out",
        )
    }

    @Test
    fun `with more legs than slots as many start as there are slots and the rest never start`(
        @TempDir output: Path,
    ) = runTest {
        val theta = scripted<ThetaConfig>("theta")
        val spin = scripted<SpinConfig>("spin")
        val portfolio = smart(theta = theta, spin = spin)

        val result = portfolio.verify(parentInjector, stubRequest(output, timeout = 120.seconds), LimitedConcurrencyGate(2), ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Timeout)
        val starts = theta.starts + spin.starts
        for (stageStart in listOf(0.seconds, 10.seconds, 40.seconds)) {
            assertThat(
                starts.count {
                    it.at == stageStart
                },
            ).describedAs("legs started at $stageStart").isEqualTo(2)
        }
        assertThat(starts).hasSize(6)
        assertThat(Regex(" cut").findAll(result.message!!).count()).isEqualTo(6)
        assertThat(Regex(" never started").findAll(result.message!!).count()).isEqualTo(7)
    }

    @Test
    fun `a configuration run in several stages keeps the artifacts of each run`(
        @TempDir output: Path,
    ) = runTest {
        val theta = scripted<ThetaConfig>("theta")
        val portfolio = smart(theta = theta, spin = scripted<SpinConfig>("spin"))

        portfolio.verify(parentInjector, stubRequest(output, timeout = 120.seconds), LimitedConcurrencyGate(9), ExecutionEnvironment.Empty, ProgressContext.Silent)

        val explicitRuns = theta.starts.filter {
            it.configuration == "cegar-expl"
        }.map {
            it.artifactOutputPath
        }
        assertThat(explicitRuns).containsExactlyInAnyOrder(
            output.resolve("first/theta-cegar-expl"),
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

        val result = portfolio.verify(parentInjector, stubRequest(output, timeout = 120.seconds), LimitedConcurrencyGate(9), ExecutionEnvironment.Empty, ProgressContext.Silent)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Timeout)
        assertThat(startsOf(theta) + startsOf(spin)).containsExactlyInAnyOrder(
            "bounded-bmc@0s",
            "safe-dfs@0s",
            "bounded-kinduction@0s",
            "cegar-expl@0s",
            "cegar-pred-cart@10s",
            "bounded-imc@10s",
            "cegar-expl-pred-combined@10s",
            "cegar-pred-cart@40s",
            "ic3@40s",
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
    fun `the second window is what is left of the third when it starts`(
        @TempDir output: Path,
    ) = runTest {
        val answers = mapOf(
            "bounded-bmc" to ScriptedAnswer(1.seconds, VerificationVerdict.Inconclusive),
            "bounded-kinduction" to ScriptedAnswer(1.seconds, VerificationVerdict.Inconclusive),
            "cegar-expl" to ScriptedAnswer(1.seconds, VerificationVerdict.Inconclusive),
        )
        val theta = scripted<ThetaConfig>("theta", answers)
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
            "cegar-pred-cart@20s",
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
            "cegar-expl@10s",
            "cegar-pred-cart@10s",
            "ic3@10s",
        )
    }

    @Test
    fun `on a case of 24 s the first stage runs within 8 s, the second is skipped and the last follows`(
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
            "cegar-expl@8s",
            "cegar-pred-cart@8s",
            "ic3@8s",
            "collapse@8s",
            "bitstate@8s",
        )
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
            "nuxmv:bmc-invar@0s",
            "theta:cegar-expl@0s",
            "nuxmv:bdd-invar@0s",
            "theta:cegar-expl@10s",
            "theta:cegar-pred-cart@10s",
            "nuxmv:ic3-invar@10s",
            "theta:bounded-imc@10s",
            "theta:cegar-expl-pred-combined@10s",
            "theta:cegar-expl@40s",
            "nuxmv:ic3-invar@40s",
            "theta:cegar-pred-cart@40s",
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
            "nuxmv:bmc-invar",
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
    fun `a staged verification cancelled from outside cancels its legs and frees their permits`(
        @TempDir output: Path,
    ) = runTest {
        val gate = LimitedConcurrencyGate(2)
        val theta = SlowBackend<ThetaConfig>("theta", delayMillis = 60_000)
        val spin = SlowBackend<SpinConfig>("spin", delayMillis = 60_000)
        val portfolio = SmartFullPortfolio(
            theta = theta,
            uppaal = PassingBackend("uppaal", UnavailableKey),
            nuxmv = PassingBackend("nuxmv", UnavailableKey),
            spin = spin,
            timeSource = testScheduler.timeSource,
        )

        val verification = launch {
            portfolio.verify(parentInjector, stubRequest(output, timeout = 120.seconds), gate, ExecutionEnvironment.Empty, ProgressContext.Silent)
        }
        advanceTimeBy(5_000)
        verification.cancelAndJoin()

        assertThat(verification.isCancelled).isTrue
        assertThat(theta.invocations.get() + spin.invocations.get()).isEqualTo(2)
        assertThat(theta.cancellations.get() + spin.cancellations.get()).isEqualTo(2)
        withTimeout(1_000) {
            coroutineScope {
                repeat(2) {
                    launch {
                        gate.withPermit {
                            delay(10)
                        }
                    }
                }
            }
        }
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
        assertThat(currentTime).isEqualTo(40_000)
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
