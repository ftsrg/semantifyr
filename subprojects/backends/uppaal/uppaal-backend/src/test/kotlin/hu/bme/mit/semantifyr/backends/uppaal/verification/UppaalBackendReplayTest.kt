/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.verification

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
import hu.bme.mit.semantifyr.backends.uppaal.ReplayingUppaalExecutor
import hu.bme.mit.semantifyr.backends.uppaal.UppaalExecutionResult
import hu.bme.mit.semantifyr.backends.uppaal.UppaalExecutionSpecification
import hu.bme.mit.semantifyr.backends.uppaal.UppaalExecutor
import hu.bme.mit.semantifyr.backends.uppaal.UppaalExecutorKey
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.Path

@InjectWithOxsts
class UppaalBackendReplayTest {

    @Inject
    private lateinit var models: BackendTestModels

    @Inject
    private lateinit var injector: Injector

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
        return UppaalBackend().verify(injector, UppaalConfig.Default, request, environment)
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
    suspend fun `an unreachable target fails without a witness`() {
        val result = verify("UnreachableTarget", models.parse(BackendTestModels.UNREACHABLE_TARGET))

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.inlinedWitness).isNull()
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
        assertThat(result.message).matches("uppaal:default does not support \\S+:2: variables of type real \\(ratio\\)")
    }

    @Test
    suspend fun `the over-approximation is NotSupported before dispatch on a havoc its candidates under-approximate`() {
        val artifactDirectory = cleanArtifactDirectory("OverApproximatedHavoc")
        val environment = ExecutionEnvironment.builder().put(UppaalExecutorKey) {
            ReplayingUppaalExecutor("OverApproximatedHavoc")
        }.build()
        val inlinedOxsts = models.parse(
            """
                inlined oxsts of semantifyr::Anything
                var havoced : int := 0
                var copy : int := 0
                init {
                }
                tran {
                    havoc(havoced)
                    copy := havoced + 1
                }
                prop {
                    AG copy < 5
                }
            """,
        )
        val request = BackendVerificationRequest(inlinedOxsts, artifactDirectory)

        val result = UppaalBackend().verify(injector, UppaalConfig.OverApproximation, request, environment)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.NotSupported)
        assertThat(result.message).matches("uppaal:over-approximation does not support \\S+:2: havoced integers read other than in comparisons with literals \\(havoced\\)")
    }

    @Test
    fun `only the over-approximation configuration over-approximates`() {
        assertThat(UppaalConfig.OverApproximation.soundness).isEqualTo(SoundnessDirection.OverApproximating)
        val underApproximating = listOf(
            UppaalConfig.Default,
            UppaalConfig.BreadthFirst,
            UppaalConfig.DepthFirst,
            UppaalConfig.RandomDepthFirst,
            UppaalConfig.UnderApproximation,
            UppaalConfig.UnderApproximationLarge,
            UppaalConfig.AggressiveInclusion,
        )
        for (config in underApproximating) {
            assertThat(config.soundness).describedAs(config.id).isEqualTo(SoundnessDirection.UnderApproximating)
        }
    }

    @Test
    suspend fun `arithmetic that may leave 32 bits under the declared ranges is NotSupported before dispatch`() {
        val result = verify(
            "UnboundedTripling",
            models.parse(
                """
                    inlined oxsts of semantifyr::Anything
                    var counter : int := 1
                    init {
                    }
                    tran {
                        counter := counter * 3
                    }
                    prop {
                        AG counter > 0
                    }
                """,
            ),
        )

        assertThat(result.verdict).isEqualTo(VerificationVerdict.NotSupported)
        assertThat(result.message).matches("uppaal:default does not support \\S+:2: arithmetic that may leave the 32-bit range \\(counter\\)")
    }

    @Test
    suspend fun `an assignment Uppaal stops on is NotSupported naming the variable`() {
        val artifactDirectory = cleanArtifactDirectory("OutOfRangeAssignment")
        val environment = ExecutionEnvironment.builder().put(UppaalExecutorKey) {
            object : UppaalExecutor {
                override fun isAvailable(): Boolean {
                    return true
                }

                override suspend fun execute(uppaalExecutionSpecification: UppaalExecutionSpecification): UppaalExecutionResult {
                    uppaalExecutionSpecification.logFile!!.writeText(" -- Aborted.\n")
                    uppaalExecutionSpecification.errorFile!!.writeText(
                        "model.xml:/nta/template[1]/transition[3]/label[1]:1: [error] Assignment of value '3' to variable 'counter' is out of range.\n",
                    )
                    return UppaalExecutionResult(1)
                }
            }
        }.build()
        val request = BackendVerificationRequest(models.parse(BackendTestModels.SATURATING_COUNTER_EXCEEDS_BOUND), artifactDirectory)
        val result = UppaalBackend().verify(injector, UppaalConfig.Default, request, environment)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.NotSupported)
        assertThat(result.message).matches("uppaal:default does not support \\S+:2: an assignment of 3 outside the declared range \\(counter\\)")
    }
}
