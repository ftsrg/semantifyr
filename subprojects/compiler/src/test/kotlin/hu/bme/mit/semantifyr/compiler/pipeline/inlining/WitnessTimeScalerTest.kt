/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.TestModels.testModel
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticBinaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class WitnessTimeScalerTest : InliningTestBase() {

    @Test
    fun `a witness's time scale multiplies the class's clock bounds and leaves the witness's own assumptions as written`() {
        val bounds = boundsOf(witness(" timescale 2"))

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

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            " timescale 1",
        ],
    )
    fun `a witness of time scale one leaves every bound as written`(timeScale: String) {
        val bounds = boundsOf(witness(timeScale))

        assertThat(
            bounds.map {
                (it as LiteralInteger).value
            },
        ).containsExactlyInAnyOrder(0, 1, 3, 7, 7)
    }

    private fun boundsOf(witness: String): List<Expression> {
        return prepareWitness(MODEL, witness) {
            inlineAll(it).inlinedOxsts.eAllOfType<ComparisonOperator>().map { comparison ->
                comparison.right
            }.toList()
        }
    }

    companion object {
        private val MODEL = testModel(WitnessTimeScalerTest::class.java, "model.semantifyr")

        private fun witness(timeScale: String): String {
            return """
                witness of clock::tests::scale::TimerFires$timeScale

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
}
