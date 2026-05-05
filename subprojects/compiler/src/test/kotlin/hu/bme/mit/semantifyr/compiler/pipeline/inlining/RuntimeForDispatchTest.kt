/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineCall
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RuntimeForDispatchTest : InliningTestBase() {
    @Test
    fun `runtime for over a containment range dispatches the inline call across each candidate`() {
        prepare(
            "Host",
            """
                package inlining::tests::runtime_for_dispatch
                class Worker {
                    var done: bool := false
                    tran step() { done := true }
                }
                @VerificationCase
                class Host {
                    contains workers: Worker[0..*]
                    contains a: Worker[1] subsets workers
                    contains b: Worker[1] subsets workers
    
                    redefine tran {
                        for (w in workers) {
                            inline w.step()
                        }
                    }
    
                    prop { return EF (a.done && b.done) }
                }
            """.trimIndent(),
        ) {
            val inlined = inlineAll(it).inlinedOxsts

            assertThat(inlined.eAllOfType<InlineCall>().toList())
                .`as`("all inline calls should be expanded, including the one with the loop variable as receiver")
                .isEmpty()
            assertThat(inlined.eAllOfType<ForOperation>().toList())
                .`as`("the runtime for loop stays in the main transition")
                .isNotEmpty()
        }
    }
}
