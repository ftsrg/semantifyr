/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.verification

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendTestModels
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.boolean
import hu.bme.mit.semantifyr.backend.enumeration
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.integer
import hu.bme.mit.semantifyr.backends.spin.ReplayingSpinExecutor
import hu.bme.mit.semantifyr.backends.spin.SpinExecutorKey
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.readText

@InjectWithOxsts
class SpinBackendReplayTest {

    @Inject
    private lateinit var models: BackendTestModels

    @Inject
    private lateinit var injector: Injector

    private fun cleanArtifactDirectory(case: String): Path {
        val directory = Path("build", "test-artifacts", "SpinBackendReplayTest", case)
        directory.toFile().deleteRecursively()
        return directory
    }

    private suspend fun verify(
        case: String,
        inlinedOxsts: InlinedOxsts,
        artifactDirectory: Path = cleanArtifactDirectory(case),
    ): BackendVerificationResult {
        val environment = ExecutionEnvironment.builder().put(SpinExecutorKey) {
            ReplayingSpinExecutor(case)
        }.build()
        val request = BackendVerificationRequest(inlinedOxsts, artifactDirectory)
        return SpinBackend().verify(injector, SpinConfig.SafeDfs, request, environment)
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
        val states = listOfNotNull(witness.initializedState) + witness.transitionStates
        assertThat(
            states.map {
                it.integer("counter")
            },
        ).containsExactly(0, 1, 2)
    }

    @Test
    suspend fun `unchanged values carry over from the declarations and the previous state`() {
        val result = verify("OneOfSeveralChanges", models.parse(BackendTestModels.SATURATING_ONE_OF_SEVERAL_CHANGES))

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        val witness = result.inlinedWitness!!
        val states = listOfNotNull(witness.initializedState) + witness.transitionStates
        assertThat(
            states.map {
                it.integer("counter")
            },
        ).containsExactly(0, 1, 2)
        assertThat(
            states.map {
                it.integer("unchanged")
            },
        ).containsExactly(5, 5, 5)
        assertThat(
            states.map {
                it.boolean("flag")
            },
        ).containsExactly(true, true, true)
    }

    @Test
    suspend fun `an unreachable target fails without a witness`() {
        val result = verify("UnreachableTarget", models.parse(BackendTestModels.UNREACHABLE_TARGET))

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.inlinedWitness).isNull()
    }

    @Test
    suspend fun `a step too long for statement merging is searched again without merging`() {
        val padding = (1..150).joinToString(" ") {
            "filler := filler + 1 filler := filler - 1"
        }
        val result = verify(
            "MergeLimit",
            models.parse(
                """
                    inlined oxsts of semantifyr::Anything
                    var counter : int := 0
                    var filler : int := 0
                    init {
                    }
                    tran {
                        assume(counter < 2) $padding
                        counter := counter + 1
                    }
                    prop {
                        AG counter < 2
                    }
                """,
            ),
        )

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        val witness = result.inlinedWitness!!
        val states = listOfNotNull(witness.initializedState) + witness.transitionStates
        assertThat(
            states.map {
                it.integer("counter")
            },
        ).containsExactly(0, 1, 2)
        assertThat(Path("build", "test-artifacts", "SpinBackendReplayTest", "MergeLimit", "spin.out").readText()).contains("merge requires more than 256 bups")
    }

    @Test
    suspend fun `a variable havoced in init can violate an invariant once initialized`() {
        val result = verifyFixture("initial_values", "UninitializedIntegerBound")

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.inlinedWitness!!.initializedState!!.integer("\$\$root\$\$x")).isNotEqualTo(0)
    }

    @Test
    suspend fun `an enum variable havoced in init can violate an invariant once initialized`() {
        val result = verifyFixture("initial_values", "UninitializedEnumBound")

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.inlinedWitness!!.initializedState!!.enumeration("\$\$root\$\$light")).isEqualTo("On")
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
        assertThat(result.message).matches("spin:safe-dfs does not support \\S+:2: variables of type real \\(ratio\\); \\S+:2: real literals")
    }

    @Test
    suspend fun `an unbounded integer the property depends on is NotSupported before dispatch`() {
        val result = verify("UnboundedCounter", models.parse(BackendTestModels.COUNTER_EXCEEDS_BOUND))

        assertThat(result.verdict).isEqualTo(VerificationVerdict.NotSupported)
        assertThat(result.message).matches("spin:safe-dfs does not support \\S+:2: unbounded integer variables \\(counter\\)")
    }

    @Test
    suspend fun `an unbounded array names its declaration once for all its slots`() {
        val artifactDirectory = cleanArtifactDirectory("UnboundedArraySlots")
        val compilation = models.compileFlattened("arrays", "ArraySlotIndependenceReach", artifactDirectory)
        val environment = ExecutionEnvironment.builder().put(SpinExecutorKey) {
            ReplayingSpinExecutor("UnboundedArraySlots")
        }.build()
        val request = BackendVerificationRequest(compilation.inlinedOxsts, artifactDirectory, compilation.flatteningInfo)
        val result = SpinBackend().verify(injector, SpinConfig.SafeDfs, request, environment)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.NotSupported)
        assertThat(result.message).matches("spin:safe-dfs does not support arrays.oxsts:\\d+: unbounded integer variables \\(slots\\)")
    }
}
