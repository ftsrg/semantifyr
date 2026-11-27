/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendTestModels
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.SoundnessDirection
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.enumeration
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.integer
import hu.bme.mit.semantifyr.backend.witness.Fraction
import hu.bme.mit.semantifyr.compiler.pipeline.context.TransitionCallTraceMap
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.Path

@InjectWithOxsts
class ThetaBackendReplayTest {

    @Inject
    private lateinit var models: BackendTestModels

    @Inject
    private lateinit var injector: Injector

    private fun cleanArtifactDirectory(case: String): Path {
        val directory = Path("build", "test-artifacts", "ThetaBackendReplayTest", case)
        directory.toFile().deleteRecursively()
        return directory
    }

    private suspend fun verify(
        case: String,
        inlinedOxsts: InlinedOxsts,
        transitionCallTraces: TransitionCallTraceMap,
        artifactDirectory: Path = cleanArtifactDirectory(case),
        config: ThetaConfig = ThetaConfig.CegarExpl,
    ): BackendVerificationResult {
        val environment = ExecutionEnvironment.builder().put(ThetaExecutorKey) {
            ReplayingThetaExecutor(case)
        }.build()
        val request = BackendVerificationRequest(inlinedOxsts, artifactDirectory, transitionCallTraces = transitionCallTraces)
        return ThetaBackend().verify(injector, config, request, environment)
    }

    private suspend fun verifyFixture(file: String, className: String): BackendVerificationResult {
        val artifactDirectory = cleanArtifactDirectory(className)
        val compilation = models.compileFlattened(file, className, artifactDirectory)
        return verify(className, compilation.inlinedOxsts, compilation.transitionCallTraces, artifactDirectory)
    }

    @Test
    suspend fun `a holding invariant passes without a witness`() {
        val result = verifyFixture("control_flow", "ChoiceBound")

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(result.inlinedWitness).isNull()
    }

    @Test
    suspend fun `a reached target passes with a witness ending in the target`() {
        val result = verifyFixture("control_flow", "ChoiceBranch1Reach")

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(result.inlinedWitness!!.transitionStates.last().integer("\$\$root\$\$x")).isEqualTo(1)
    }

    @Test
    suspend fun `a violated invariant fails with a witness through the violating states`() {
        val result = verify("CounterExceedsBound", models.parse(BackendTestModels.SATURATING_COUNTER_EXCEEDS_BOUND), TransitionCallTraceMap(emptyMap()))

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        val witness = result.inlinedWitness!!
        val states = listOf(witness.initializedState) + witness.transitionStates
        assertThat(
            states.map {
                it.integer("counter")
            },
        ).containsExactly(0, 1, 2)
    }

    @Test
    suspend fun `witness values keep the declaration order`() {
        val result = verify("OneOfSeveralChanges", models.parse(BackendTestModels.SATURATING_ONE_OF_SEVERAL_CHANGES), TransitionCallTraceMap(emptyMap()))

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        val witness = result.inlinedWitness!!
        val states = listOf(witness.initializedState) + witness.transitionStates
        assertThat(
            states.map { state ->
                state.values.map {
                    it.variable.name
                }
            },
        ).containsOnly(listOf("counter", "unchanged", "flag"))
    }

    @Test
    suspend fun `a model without state variables fails with an empty witness`() {
        val statelessViolation = models.parse(
            """
                inlined oxsts of semantifyr::Anything
                init {
                }
                tran {
                    choice {
                    } or {
                        assume (true)
                    }
                }
                prop {
                    AG false
                }
            """,
        )

        val result = verify("StatelessViolation", statelessViolation, TransitionCallTraceMap(emptyMap()))

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.inlinedWitness!!.initializedState.values).isEmpty()
    }

    @Test
    suspend fun `a violation found by Horn solving fails without a witness and is not trusted`() {
        val result = verify("HornViolation", models.parse(BackendTestModels.SATURATING_COUNTER_EXCEEDS_BOUND), TransitionCallTraceMap(emptyMap()), config = ThetaConfig.Chc)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.inlinedWitness).isNull()
        assertThat(result.isTrusted).isFalse
        assertThat(result.soundness).isEqualTo(SoundnessDirection.OverApproximating)
    }

    @Test
    suspend fun `an unreachable target fails without a witness`() {
        val result = verify("UnreachableTarget", models.parse(BackendTestModels.UNREACHABLE_TARGET), TransitionCallTraceMap(emptyMap()))

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.inlinedWitness).isNull()
    }

    @Test
    suspend fun `a witness's initialized state holds the value a variable was havoced to in init`() {
        val result = verifyFixture("initial_values", "UninitializedIntegerBound")

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.inlinedWitness!!.initializedState.integer("\$\$root\$\$x")).isNotEqualTo(0)
    }

    @Test
    suspend fun `a witness's initialized state holds the literal an enum variable was havoced to in init`() {
        val result = verifyFixture("initial_values", "UninitializedEnumBound")

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.inlinedWitness!!.initializedState.enumeration("\$\$root\$\$light")).isEqualTo("On")
    }

    @Test
    suspend fun `unsupported constructs are NotSupported with their source location`() {
        val result = verify(
            "RealVariable",
            models.parse(
                """
                    inlined oxsts of semantifyr::Anything
                    var ratio : real := 0.5
                    init {
                    }
                    tran {
                    }
                    prop {
                        AG true
                    }
                """,
            ),
            TransitionCallTraceMap(emptyMap()),
        )

        assertThat(result.verdict).isEqualTo(VerificationVerdict.NotSupported)
        assertThat(result.message).matches("theta:cegar-expl does not support \\S+:2: variables of type real \\(ratio\\); \\S+:2: real literals")
    }

    @Test
    suspend fun `racing legs on one request do not interfere`() {
        val artifactDirectory = cleanArtifactDirectory("RacingLegs")
        val compilation = models.compileFlattened("control_flow", "ChoiceBranch1Reach", artifactDirectory)
        val environment = ExecutionEnvironment.builder().put(ThetaExecutorKey) {
            ReplayingThetaExecutor("ChoiceBranch1Reach")
        }.build()

        val results = coroutineScope {
            (0 until 16).map { leg ->
                async(Dispatchers.Default) {
                    val request = BackendVerificationRequest(compilation.inlinedOxsts, artifactDirectory.resolve("leg$leg"), transitionCallTraces = compilation.transitionCallTraces)
                    ThetaBackend().verify(injector, ThetaConfig.CegarExpl, request, environment)
                }
            }.awaitAll()
        }

        assertThat(
            results.map {
                "${it.verdict} ${it.message}"
            },
        ).containsOnly("Passed null")
        assertThat(
            results.map {
                it.inlinedWitness
            },
        ).doesNotContainNull()
    }

    @Test
    suspend fun `a witness through a runtime for operation reads back past the loop variable`() {
        val result = verifyFixture("runtime_for", "InclusiveRangeReach")

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(result.inlinedWitness!!.transitionStates.last().integer("\$\$root\$\$x")).isEqualTo(3)
    }

    @Test
    suspend fun `a clock trace states every kept clock's value after the step and the whole delays that passed`() {
        val result = verifyFixture("clocks", "ClockZeroDelayAfterResetViolated")

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        val states = result.inlinedWitness!!.transitionStates
        assertThat(
            states.last().clockValues.associate {
                it.variable.name to it.value
            },
        ).containsExactly(
            entry("\$\$root\$\$lap", Fraction.ZERO),
            entry("\$\$root\$\$total", Fraction.of(1)),
        )
        assertThat(
            states.fold(Fraction.ZERO) { sum, state ->
                sum + state.elapsed
            },
        ).isEqualTo(Fraction.of(1))
    }
}
