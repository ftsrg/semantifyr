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
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.enumeration
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.integer
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.assertj.core.api.Assertions.assertThat
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
        artifactDirectory: Path = cleanArtifactDirectory(case),
    ): BackendVerificationResult {
        val environment = ExecutionEnvironment.builder().put(ThetaExecutorKey) {
            ReplayingThetaExecutor(case)
        }.build()
        val request = BackendVerificationRequest(inlinedOxsts, artifactDirectory)
        return ThetaBackend().verify(injector, ThetaConfig.CegarExpl, request, environment)
    }

    private suspend fun verifyFixture(file: String, className: String): BackendVerificationResult {
        val artifactDirectory = cleanArtifactDirectory(className)
        return verify(className, models.compile(file, className, artifactDirectory), artifactDirectory)
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
        val result = verify("CounterExceedsBound", models.parse(BackendTestModels.SATURATING_COUNTER_EXCEEDS_BOUND))

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
    suspend fun `a counterexample that does not parse fails the witness and keeps the verdict`() {
        val result = verify("MalformedCounterexample", models.parse(BackendTestModels.SATURATING_COUNTER_EXCEEDS_BOUND))

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.inlinedWitness).isNull()
        assertThat(result.message).contains("(counter #b1)")
    }

    @Test
    suspend fun `witness values keep the declaration order`() {
        val result = verify("OneOfSeveralChanges", models.parse(BackendTestModels.SATURATING_ONE_OF_SEVERAL_CHANGES))

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
        val result = verify("StatelessViolation", models.parse(BackendTestModels.STATELESS_VIOLATION))

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.inlinedWitness!!.initializedState.values).isEmpty()
    }

    @Test
    suspend fun `an unreachable target fails without a witness`() {
        val result = verify("UnreachableTarget", models.parse(BackendTestModels.UNREACHABLE_TARGET))

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.inlinedWitness).isNull()
    }

    @Test
    suspend fun `a variable havoced in init can violate an invariant once initialized`() {
        val result = verifyFixture("initial_values", "UninitializedIntegerBound")

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.inlinedWitness!!.initializedState.integer("\$\$root\$\$x")).isNotEqualTo(0)
    }

    @Test
    suspend fun `an enum variable havoced in init can violate an invariant once initialized`() {
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
        )

        assertThat(result.verdict).isEqualTo(VerificationVerdict.NotSupported)
        assertThat(result.message).matches("theta:cegar-expl does not support \\S+:2: variables of type real \\(ratio\\); \\S+:2: real literals")
    }

    @Test
    suspend fun `racing legs on one request do not interfere`() {
        val artifactDirectory = cleanArtifactDirectory("RacingLegs")
        val inlinedOxsts = models.compile("control_flow", "ChoiceBranch1Reach", artifactDirectory)
        val environment = ExecutionEnvironment.builder().put(ThetaExecutorKey) {
            ReplayingThetaExecutor("ChoiceBranch1Reach")
        }.build()

        val results = coroutineScope {
            (0 until 16).map { leg ->
                async(Dispatchers.Default) {
                    val request = BackendVerificationRequest(inlinedOxsts, artifactDirectory.resolve("leg$leg"))
                    ThetaBackend().verify(injector, ThetaConfig.CegarExpl, request, environment)
                }
            }.awaitAll()
        }

        assertThat(results.map { "${it.verdict} ${it.message}" }).containsOnly("Passed null")
        assertThat(results.map { it.inlinedWitness }).doesNotContainNull()
    }

    @Test
    suspend fun `a runtime for operation iterates its whole inclusive range`() {
        val result = verifyFixture("runtime_for", "InclusiveRangeReach")

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(result.inlinedWitness!!.transitionStates.last().integer("\$\$root\$\$x")).isEqualTo(3)
    }

    @Test
    suspend fun `a runtime for operation stops before the end of an exclusive range`() {
        val result = verifyFixture("runtime_for", "ExclusiveRangeBound")

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
    }
}
