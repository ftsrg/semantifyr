/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.verification

import hu.bme.mit.semantifyr.backend.BackendTestModels
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.valuesByName
import hu.bme.mit.semantifyr.backends.uppaal.ReplayingUppaalExecutor
import hu.bme.mit.semantifyr.backends.uppaal.UppaalExecutorKey
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.Path

class UppaalBackendReplayTest {

    companion object {
        private val models = BackendTestModels()
    }

    private fun cleanArtifactDirectory(case: String): Path {
        val directory = Path("build", "test-artifacts", "UppaalBackendReplayTest", case)
        directory.toFile().deleteRecursively()
        return directory
    }

    private suspend fun verify(
        case: String,
        inlinedOxsts: InlinedOxsts,
        artifactDirectory: Path = cleanArtifactDirectory(case),
    ): BackendVerificationResult {
        val environment = ExecutionEnvironment.builder().put(UppaalExecutorKey) {
            ReplayingUppaalExecutor(case)
        }.build()
        val request = BackendVerificationRequest(inlinedOxsts, artifactDirectory)
        return UppaalBackend().verify(models.injector, UppaalConfig.Default, request, environment)
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
        assertThat(result.inlinedWitness!!.transitionStates.last().valuesByName()).containsEntry("\$\$root\$\$x", 1)
    }

    @Test
    suspend fun `a violated invariant fails with a witness through the violating states`() {
        val result = verify("CounterExceedsBound", models.parse(BackendTestModels.COUNTER_EXCEEDS_BOUND))

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        val witness = result.inlinedWitness!!
        val states = listOfNotNull(witness.initializedState) + witness.transitionStates
        assertThat(states.map { it.valuesByName()["x"] }).containsExactly(0, 1, 2)
    }

    @Test
    suspend fun `an unreachable target fails without a witness`() {
        val result = verify("UnreachableTarget", models.parse(BackendTestModels.UNREACHABLE_TARGET))

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.inlinedWitness).isNull()
    }

    @Test
    suspend fun `an uninitialized variable can violate an invariant in the initial state`() {
        val result = verifyFixture("initial_values", "UninitializedIntegerBound")

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.inlinedWitness!!.initializedState!!.valuesByName()["\$\$root\$\$x"]).isNotNull().isNotEqualTo(0)
    }

    @Test
    suspend fun `an uninitialized enum variable can violate an invariant in the initial state`() {
        val result = verifyFixture("initial_values", "UninitializedEnumBound")

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.inlinedWitness!!.initializedState!!.valuesByName()["\$\$root\$\$light"]).isEqualTo("On")
    }

    @Test
    suspend fun `unsupported constructs are NotSupported with their source location`() {
        val result = verify(
            "RealVariable",
            models.parse(
                """
                    inlined oxsts of semantifyr::Anything
                    var r : real := 0.5
                    init { }
                    tran { }
                    prop { AG true }
                """,
            ),
        )

        assertThat(result.verdict).isEqualTo(VerificationVerdict.NotSupported)
        assertThat(result.message).matches("uppaal:default does not support \\S+:2: variables of type real \\(r\\)")
    }
}
