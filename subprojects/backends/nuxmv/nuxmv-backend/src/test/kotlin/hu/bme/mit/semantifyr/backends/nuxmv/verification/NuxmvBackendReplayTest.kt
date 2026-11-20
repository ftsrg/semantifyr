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
import hu.bme.mit.semantifyr.backend.boolean
import hu.bme.mit.semantifyr.backend.enumeration
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.integer
import hu.bme.mit.semantifyr.backend.witness.Fraction
import hu.bme.mit.semantifyr.backends.nuxmv.NuxmvExecutorKey
import hu.bme.mit.semantifyr.backends.nuxmv.ReplayingNuxmvExecutor
import hu.bme.mit.semantifyr.compiler.pipeline.context.TransitionCallTraceMap
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.assertj.core.api.Assertions.assertThat
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
        transitionCallTraces: TransitionCallTraceMap,
        artifactDirectory: Path = cleanArtifactDirectory(case),
        config: NuxmvConfig = NuxmvConfig.Ic3Invar,
    ): BackendVerificationResult {
        val environment = ExecutionEnvironment.builder().put(NuxmvExecutorKey) {
            ReplayingNuxmvExecutor(case)
        }.build()
        val request = BackendVerificationRequest(inlinedOxsts, artifactDirectory, transitionCallTraces = transitionCallTraces)
        return NuxmvBackend().verify(injector, config, request, environment)
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
    suspend fun `the commands hide the auxiliary variables from the trace`() {
        verifyFixture("control_flow", "ChoiceBranch1Reach")

        val commands = Path("build", "test-artifacts", "NuxmvBackendReplayTest", "ChoiceBranch1Reach", "commands.cmd").readText()
        assertThat(commands).contains("set traces_hiding_prefix \"~\"")
    }

    @Test
    suspend fun `the bound of a configuration reaches the check command`() {
        val artifactDirectory = cleanArtifactDirectory("ChoiceBound")
        val compilation = models.compileFlattened("control_flow", "ChoiceBound", artifactDirectory)

        verify("ChoiceBound", compilation.inlinedOxsts, compilation.transitionCallTraces, artifactDirectory, NuxmvConfig.KInductionInvar.copy(bound = 50))

        val commands = artifactDirectory.resolve("commands.cmd").readText()
        assertThat(commands).contains("msat_check_invar_bmc -a een-sorensson -k 50 -p \"")
    }

    @Test
    suspend fun `a clock trace states each step's delay as its exact elapsed time`() {
        val result = verifyFixture("clocks", "ClockStrictTestDenseReach")

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        val witness = result.inlinedWitness!!
        assertThat(witness.initializedState.elapsed).isEqualTo(Fraction.ZERO)
        val reached = witness.transitionStates.last()
        assertThat(reached.boolean("\$\$root\$\$between")).isTrue()
        assertThat(reached.elapsed.numerator.signum()).isPositive()
        assertThat(reached.elapsed.numerator).isLessThan(reached.elapsed.denominator)
    }

    @Test
    suspend fun `a clock trace states every kept clock's value after the step`() {
        val result = verifyFixture("clocks", "ClockZeroDelayAfterResetViolated")

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        val reached = result.inlinedWitness!!.transitionStates.last()
        val clockValues = reached.clockValues.associate {
            it.variable.name to it.value
        }
        assertThat(clockValues.getValue("\$\$root\$\$lap")).isEqualTo(Fraction.ZERO)
        assertThat(clockValues.getValue("\$\$root\$\$total")).isEqualTo(Fraction.of(1))
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
        assertThat(result.message).matches("nuxmv:ic3-invar does not support \\S+:2: variables of type real \\(ratio\\)")
    }
}
