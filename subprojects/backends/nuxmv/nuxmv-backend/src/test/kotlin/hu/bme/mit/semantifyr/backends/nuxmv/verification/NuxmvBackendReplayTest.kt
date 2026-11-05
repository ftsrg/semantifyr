/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.verification

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendTestModels
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.enumeration
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.integer
import hu.bme.mit.semantifyr.backends.nuxmv.NuxmvExecutorKey
import hu.bme.mit.semantifyr.backends.nuxmv.ReplayingNuxmvExecutor
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.readText

@InjectWithOxsts
class NuxmvBackendReplayTest {

    @Inject
    private lateinit var models: BackendTestModels

    @Inject
    private lateinit var injector: Injector

    private fun cleanArtifactDirectory(case: String): Path {
        val directory = Path("build", "test-artifacts", "NuxmvBackendReplayTest", case)
        directory.toFile().deleteRecursively()
        return directory
    }

    private suspend fun verify(
        case: String,
        inlinedOxsts: InlinedOxsts,
        artifactDirectory: Path = cleanArtifactDirectory(case),
        config: NuxmvConfig = NuxmvConfig.Ic3Invar,
    ): BackendVerificationResult {
        val environment = ExecutionEnvironment.builder().put(NuxmvExecutorKey) {
            ReplayingNuxmvExecutor(case)
        }.build()
        val request = BackendVerificationRequest(inlinedOxsts, artifactDirectory)
        return NuxmvBackend().verify(injector, config, request, environment)
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
        val commands = Path("build", "test-artifacts", "NuxmvBackendReplayTest", "ChoiceBranch1Reach", "commands.cmd").readText()
        assertThat(commands).contains("set traces_hiding_prefix \"~\"")
    }

    @Test
    suspend fun `the bound of a configuration reaches the check command`() {
        val artifactDirectory = cleanArtifactDirectory("ChoiceBound")
        val inlinedOxsts = models.compile("control_flow", "ChoiceBound", artifactDirectory)

        verify("ChoiceBound", inlinedOxsts, artifactDirectory, NuxmvConfig.BmcInvar.copy(bound = 50))

        val commands = artifactDirectory.resolve("commands.cmd").readText()
        assertThat(commands).contains("msat_check_invar_bmc -a een-sorensson -k 50 -p \"")
    }

    @Test
    fun `a bound must be positive`() {
        assertThatThrownBy {
            NuxmvConfig.BmcInvar.copy(bound = 0)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    suspend fun `choices in several transition branches are decided independently`() {
        val result = verify(
            "WitnessShapedChoices",
            models.parse(
                """
                    inlined oxsts of semantifyr::Anything
                    var chosen : int := 0
                    var step : int := 0
                    init {
                    }
                    tran {
                        assume(step == 0)
                        choice {
                            chosen := 1
                        } or {
                            chosen := 2
                        }
                        step := 1
                    } or {
                        assume(step == 1)
                        choice {
                            chosen := 3
                        } or {
                            chosen := 4
                        }
                        step := 2
                    }
                    prop {
                        EF chosen == 4
                    }
                """,
            ),
        )

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(result.inlinedWitness!!.transitionStates.last().integer("chosen")).isEqualTo(4)
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
    suspend fun `an unreachable target fails without a witness`() {
        val result = verify("UnreachableTarget", models.parse(BackendTestModels.UNREACHABLE_TARGET))

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.inlinedWitness).isNull()
    }

    @Test
    suspend fun `an uninitialized variable can violate an invariant in the initial state`() {
        val result = verifyFixture("initial_values", "UninitializedIntegerBound")

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.inlinedWitness!!.initializedState!!.integer("\$\$root\$\$x")).isNotEqualTo(0)
    }

    @Test
    suspend fun `an uninitialized enum variable can violate an invariant in the initial state`() {
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
        assertThat(result.message).matches("nuxmv:ic3-invar does not support \\S+:2: variables of type real \\(ratio\\)")
    }
}
