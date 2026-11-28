/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RuntimeForInliningTest : InliningTestBase() {

    @Test
    fun `a non-inline for over an integer range stays a for in the inlined main transition`() {
        prepare(
            "Host",
            """
                package inlining::tests
                @VerificationCase
                class Host {
                    var total: int := 0
                    redefine tran {
                        for (index in 1..3) {
                            total := total + index
                        }
                    }
                    redefine prop {
                        return EF total == 6
                    }
                }
            """,
        ) {
            val inlined = inlineAll(it).inlinedOxsts

            assertThat(inlined.mainTransition.eAllOfType<ForOperation>().toList()).hasSize(1)
        }
    }
}
