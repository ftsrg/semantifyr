/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.flattening

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StructureFlatteningTest : FlatteningTestBase() {
    @Test
    fun `a shared containment is one instance in every holder`() {
        val flattened = flatten(
            "Host",
            """
                package flattening::tests
                class Counter {
                    var count : int := 0
                }
                class User {
                    @Shared
                    contains counter : Counter[1]
                    contains own : Counter[1]
                }
                @VerificationCase
                class Host {
                    contains u1 : User[1]
                    contains u2 : User[1]
                    redefine tran {
                        u1.counter.count := 1
                        u1.own.count := 1
                        u2.own.count := 2
                    }
                    prop { return AG (u2.counter.count == 0 || u2.counter.count == 1) }
                }
            """,
        )

        assertThat(flattened.inlinedOxsts.variables.map { it.name })
            .hasSize(3)
        assertThat(serialize(flattened.inlinedOxsts.property))
            .doesNotContain("u2")
    }

    @Test
    fun `branches of the main and the initial transition stay alternatives`() {
        val flattened = flatten(
            "Host",
            """
                package flattening::tests
                @VerificationCase
                class Host {
                    var x : int := 0
                    redefine init {
                        x := 1
                    } or {
                        x := 2
                    }
                    redefine tran {
                        x := x + 1
                    } or {
                        x := x - 1
                    }
                    prop { return AG true }
                }
            """,
        )

        assertThat(flattened.inlinedOxsts.initTransition.eAllOfType<ChoiceOperation>().single().branches).hasSize(2)
        assertThat(flattened.inlinedOxsts.mainTransition.eAllOfType<ChoiceOperation>().single().branches).hasSize(2)
    }
}
