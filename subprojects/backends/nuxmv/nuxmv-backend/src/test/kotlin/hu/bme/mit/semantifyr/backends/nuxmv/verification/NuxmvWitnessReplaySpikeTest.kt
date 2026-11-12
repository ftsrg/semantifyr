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
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.witness.WitnessReplay
import hu.bme.mit.semantifyr.backends.nuxmv.NuxmvExecutorKey
import hu.bme.mit.semantifyr.backends.nuxmv.ReplayingNuxmvExecutor
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeLines

@InjectWithOxsts
class NuxmvWitnessReplaySpikeTest {

    @Inject
    private lateinit var models: BackendTestModels

    @Inject
    private lateinit var injector: Injector

    @Inject
    private lateinit var replay: WitnessReplay

    private val outputDirectory = Path("build", "test-artifacts", "NuxmvWitnessReplaySpikeTest")

    @Test
    suspend fun `replays the captured nuXmv witnesses`() {
        val lines = mutableListOf<String>()
        lines += replayCaptured("ChoiceBranch1Reach", models.compile("control_flow", "ChoiceBranch1Reach", outputDirectory.resolve("ChoiceBranch1Reach")))
        lines += replayCaptured("CounterExceedsBound", models.parse(BackendTestModels.SATURATING_COUNTER_EXCEEDS_BOUND))
        lines += replayCaptured("UninitializedIntegerBound", models.compile("initial_values", "UninitializedIntegerBound", outputDirectory.resolve("UninitializedIntegerBound")))
        lines += replayCaptured("UninitializedEnumBound", models.compile("initial_values", "UninitializedEnumBound", outputDirectory.resolve("UninitializedEnumBound")))
        lines += replayCaptured(
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
        outputDirectory.createDirectories()
        outputDirectory.resolve("nuxmv.txt").writeLines(lines)
    }

    private suspend fun replayCaptured(case: String, inlinedOxsts: InlinedOxsts): String {
        val environment = ExecutionEnvironment.builder().put(NuxmvExecutorKey) {
            ReplayingNuxmvExecutor(case)
        }.build()
        val artifactDirectory = outputDirectory.resolve(case).resolve("run")
        val result = NuxmvBackend().verify(injector, NuxmvConfig.Ic3Invar, BackendVerificationRequest(inlinedOxsts, artifactDirectory), environment)
        val witness = result.inlinedWitness ?: return "nuxmv | $case | ${result.verdict} | no witness"
        val replayed = replay.replay(witness, null)
        val failure = replayed.firstFailure
        val reason = if (failure == null) {
            ""
        } else {
            "step ${failure.index}: ${failure.reason}"
        }
        return "nuxmv | $case | ${result.verdict} | ${replayed.outcome} | ${witness.transitionStates.size + 1} states | most ${replayed.mostPathsInStep} total ${replayed.exploredPaths} paths | exercised ${replayed.exercised.sorted()} | $reason"
    }
}
