/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.flattening

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.TestModels.testModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DivisionAssumptionsTest : FlatteningTestBase() {

    @Test
    fun `the operation that evaluates a division assumes its divisor nonzero, an inner divisor first`() {
        assertFlattenedModel(
            "Host",
            testModel(javaClass, "nested_divisions.semantifyr"),
            $$$$"""
                init {
                }

                tran {
                    havoc('$$root$$divisor')
                    assume('$$root$$divisor' != 0)
                    assume(6 / '$$root$$divisor' != 0)
                    '$$root$$result' := 12 / (6 / '$$root$$divisor')
                    assume('$$root$$divisor' != 0)
                    if ('$$root$$result' / '$$root$$divisor' > 1) {
                        '$$root$$result' := 0
                    }
                }

                prop {
                    AG '$$root$$result' >= 0
                }
            """,
        )
    }

    @Test
    fun `no fold erases the assumption of a division the optimizer multiplies by zero`() {
        val flattened = flatten(
            "Host",
            """
                package flattening::division
                @VerificationCase
                class Host {
                    var divisor: int := 0
                    var result: int := 1
                    redefine tran {
                        havoc(divisor)
                        result := (1 / divisor) * 0
                    }
                    redefine prop {
                        return AG result == 1
                    }
                }
            """,
            optimization = OptimizationConfig.ALL,
        )

        assertThat(serialize(flattened.inlinedOxsts.mainTransition)).contains("assume('\$\$root\$\$divisor' != 0)")
    }

}
