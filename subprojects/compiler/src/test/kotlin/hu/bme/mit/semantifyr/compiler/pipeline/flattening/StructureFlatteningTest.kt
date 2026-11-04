/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.flattening

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
                    contains firstUser : User[1]
                    contains secondUser : User[1]
                    redefine tran {
                        firstUser.counter.count := 1
                        firstUser.own.count := 1
                        secondUser.own.count := 2
                    }
                    prop { return AG (secondUser.counter.count == 0 || secondUser.counter.count == 1) }
                }
            """,
        )

        assertThat(
            flattened.inlinedOxsts.variables.map {
                it.name
            },
        ).hasSize(3)
        assertThat(serialize(flattened.inlinedOxsts.property))
            .doesNotContain("secondUser")
    }

    @Test
    fun `a container feature navigates to the instance that contains it`() {
        assertFlattenedModel(
            "Host",
            """
                package flattening::tests
                class Parent {
                    contains child : Offspring[1] opposite parent
                    var generation : int := 0
                }
                class Offspring {
                    container parent : Parent[1] opposite child
                    tran bumpParent() {
                        assume(parent.generation < 3)
                        parent.generation := parent.generation + 1
                    }
                }
                @VerificationCase
                class Host {
                    contains parent : Parent[1]
                    redefine tran { inline parent.child.bumpParent() }
                    prop { return EF parent.generation == 3 }
                }
            """,
            $$$$"""
                init {
                }

                tran {
                    assume ('$$root$$parent$$generation' < 3)
                    '$$root$$parent$$generation' := '$$root$$parent$$generation' + 1
                }

                prop {
                    EF '$$root$$parent$$generation' == 3
                }
            """,
        )
    }

    @Test
    fun `a member read through an absent optional instance fails compilation`() {
        assertThatThrownBy {
            flatten(
                "Host",
                """
                    package flattening::tests
                    class Leaf {
                        var count : int := 0
                    }
                    @VerificationCase
                    class Host {
                        contains leaf : Leaf[0..1]
                        prop { return AG leaf?.count == 0 }
                    }
                """,
            )
        }.hasMessageContaining("This expression is not evaluable to a single instance!")
    }

    @Test
    fun `branches of the main and the initial transition stay alternatives`() {
        val flattened = flatten(
            "Host",
            """
                package flattening::tests
                @VerificationCase
                class Host {
                    var counter : int := 0
                    redefine init {
                        counter := 1
                    } or {
                        counter := 2
                    }
                    redefine tran {
                        counter := counter + 1
                    } or {
                        counter := counter - 1
                    }
                    prop { return AG true }
                }
            """,
        )

        assertThat(flattened.inlinedOxsts.initTransition.eAllOfType<ChoiceOperation>().single().branches).hasSize(2)
        assertThat(flattened.inlinedOxsts.mainTransition.eAllOfType<ChoiceOperation>().single().branches).hasSize(2)
    }
}
