/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.BackendTestModels
import hu.bme.mit.semantifyr.backend.SoundnessDirection
import hu.bme.mit.semantifyr.backend.witness.Fraction
import hu.bme.mit.semantifyr.backend.witness.WitnessFailedException
import hu.bme.mit.semantifyr.backend.witness.WitnessState
import hu.bme.mit.semantifyr.backend.witness.WitnessStateValue
import hu.bme.mit.semantifyr.backend.witness.linearInlinedWitness
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@InjectWithOxsts
class WholeDelayTest {

    @Inject
    private lateinit var models: BackendTestModels

    @Inject
    private lateinit var wholeDelayLowering: WholeDelayLowering

    private fun parse(body: String): InlinedOxsts {
        return models.parse("inlined oxsts of semantifyr::Anything\n${body.trimIndent()}")
    }

    private val deadline = """
        var elapsed : clock := 0
        var late : bool := false
        init {
        }
        tran {
            assume(elapsed <= 2)
            choice {
                assume(elapsed >= 2)
                late := true
                elapsed := 0
            } or {
            }
        }
        prop {
            AG !late
        }
    """

    @Test
    fun `a model without clocks waits for nothing`() {
        val inlinedOxsts = parse(
            """
                var counter : int := 0
                init {
                }
                tran {
                    counter := counter + 1
                }
                prop {
                    AG counter < 5
                }
            """,
        )

        assertThat(wholeDelayLowering.lower(inlinedOxsts)).isNull()
    }

    @Test
    fun `a clock saturates one above the largest bound of its tests, from either side and through a choice of clocks`() {
        val inlinedOxsts = parse(
            """
                var first : clock := 0
                var second : clock := 0
                var pickFirst : bool := false
                init {
                }
                tran {
                    assume((if pickFirst then first else second) <= 4)
                    assume(7 >= first)
                    havoc(pickFirst)
                }
                prop {
                    AG true
                }
            """,
        )

        val wholeDelay = wholeDelayLowering.lower(inlinedOxsts)!!

        val saturations = wholeDelay.clocks.associate {
            it.variable.name to it.saturation
        }
        assertThat(saturations).containsExactlyEntriesOf(
            mapOf(
                "first" to 8,
                "second" to 5,
            ),
        )
        assertThat(wholeDelay.longestDelay).isEqualTo(8)
    }

    @Test
    fun `a clock never compared saturates at 1`() {
        val inlinedOxsts = parse(
            """
                var elapsed : clock := 0
                init {
                }
                tran {
                    elapsed := 0
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(wholeDelayLowering.lower(inlinedOxsts)!!.clocks.single().saturation).isEqualTo(1)
    }

    @Test
    fun `a model whose clock tests are closed keeps the configured direction`() {
        val inlinedOxsts = parse(deadline)

        assertThat(wholeDelayLowering.soundness(inlinedOxsts, SoundnessDirection.Exact)).isEqualTo(SoundnessDirection.Exact)
    }

    @Test
    fun `a model with a strict clock test under-approximates`() {
        val inlinedOxsts = parse(
            """
                var elapsed : clock := 0
                var between : bool := false
                init {
                }
                tran {
                    assume(elapsed <= 1)
                    choice {
                        assume(elapsed > 0 && elapsed < 1)
                        between := true
                    } or {
                    }
                    elapsed := 0
                }
                prop {
                    EF between
                }
            """,
        )

        assertThat(wholeDelayLowering.soundness(inlinedOxsts, SoundnessDirection.Exact)).isEqualTo(SoundnessDirection.UnderApproximating)
    }

    @Test
    fun `a saturated clock reads back as its value before the step plus the elapsed time, and the clock leaves the state values`() {
        val inlinedOxsts = parse(deadline)
        val wholeDelay = wholeDelayLowering.lower(inlinedOxsts)!!
        val elapsed = inlinedOxsts.variables.first {
            it.name == "elapsed"
        }
        val late = inlinedOxsts.variables.first {
            it.name == "late"
        }

        fun state(clock: Int, delay: Long): WitnessState {
            return WitnessState(
                listOf(
                    WitnessStateValue(elapsed, OxstsFactory.createLiteralInteger(clock)),
                    WitnessStateValue(late, OxstsFactory.createLiteralBoolean(false)),
                ),
                elapsed = Fraction.of(delay),
            )
        }

        val witness = linearInlinedWitness(
            inlinedOxsts,
            listOf(
                state(0, 0),
                state(2, 2),
                state(3, 3),
                state(0, 1),
            ),
        )

        val restored = wholeDelay.restore(witness)

        val states = listOf(restored.initializedState) + restored.transitionStates
        assertThat(
            states.map {
                it.clockValues.single().value
            },
        ).containsExactly(
            Fraction.of(0),
            Fraction.of(2),
            Fraction.of(5),
            Fraction.of(0),
        )
        assertThat(
            states.map {
                it.elapsed
            },
        ).containsExactly(
            Fraction.of(0),
            Fraction.of(2),
            Fraction.of(3),
            Fraction.of(1),
        )
        assertThat(
            states.flatMap {
                it.values
            }.map {
                it.variable
            }.distinct(),
        ).containsExactly(late)
        assertThat(restored.getNextStates(restored.initializedState)).containsExactly(restored.transitionStates.first())
    }

    @Test
    fun `a clock beyond its saturation fails the read-back`() {
        val inlinedOxsts = parse(deadline)
        val wholeDelay = wholeDelayLowering.lower(inlinedOxsts)!!
        val elapsed = inlinedOxsts.variables.first {
            it.name == "elapsed"
        }
        val witness = linearInlinedWitness(
            inlinedOxsts,
            listOf(
                WitnessState(
                    listOf(
                        WitnessStateValue(elapsed, OxstsFactory.createLiteralInteger(4)),
                    ),
                ),
            ),
        )

        assertThrows<WitnessFailedException> {
            wholeDelay.restore(witness)
        }
    }
}
