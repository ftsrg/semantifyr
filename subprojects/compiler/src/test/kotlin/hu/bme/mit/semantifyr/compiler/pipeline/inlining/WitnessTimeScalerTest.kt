/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticBinaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WitnessTimeScalerTest : InliningTestBase() {

    @Test
    fun `a witness's time scale multiplies the class's clock bounds and leaves the witness's own assumptions as written`() {
        val bounds = prepareWitness(MODEL, WITNESS) {
            inlineAll(it).inlinedOxsts.eAllOfType<ComparisonOperator>().map { comparison ->
                comparison.right
            }.toList()
        }

        assertThat(bounds.filterIsInstance<ArithmeticBinaryOperator>()).singleElement().satisfies({
            assertThat(it.op).isEqualTo(ArithmeticOp.MUL)
            assertThat((it.left as LiteralInteger).value).isEqualTo(3)
            assertThat((it.right as LiteralInteger).value).isEqualTo(2)
        })
        assertThat(
            bounds.filterIsInstance<LiteralInteger>().map {
                it.value
            },
        ).containsExactlyInAnyOrder(0, 1, 7, 7)
    }

    companion object {
        private val MODEL = """
            package clock::tests::scale

            class Timer {
                var elapsed: clock := 0
                var fired: bool := false

                tran fire() {
                    assume(elapsed >= 3)
                    fired := true
                }
            }

            @VerificationCase
            class TimerFires {
                contains timer: Timer

                redefine tran {
                    inline timer.fire()
                }

                prop {
                    return EF (timer.fired)
                }
            }
        """.trimIndent()

        private val WITNESS = """
            witness of clock::tests::scale::TimerFires timescale 2

            var step: int := 0

            var time: clock := 0

            contains root: clock::tests::scale::TimerFires

            init {
                assume(step == 0)
                inline root.init()
                step := 1
            }

            tran {
                assume(step == 1)
                inline root.main()
                assume(time == 7)
                assume(root.timer.elapsed == 7)
                step := 2
            }

            prop {
                root.prop()
            }
        """.trimIndent()
    }
}
