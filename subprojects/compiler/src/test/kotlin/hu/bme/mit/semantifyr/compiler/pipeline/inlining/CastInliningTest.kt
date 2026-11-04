/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CastExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TemporalOperator
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CastInliningTest : InliningTestBase() {
    @Test
    fun `cast around a temporal operator at the property level is removed during inlining`() {
        prepare(
            "Case",
            """
                package inlining::tests::cast_in_property
                @VerificationCase
                class Case {
                    var counter: int := 0
                    redefine tran {
                        counter := counter + 1
                    }
                    prop {
                        return (EF counter == 5) as bool
                    }
                }
            """,
        ) {
            val inlined = inlineAll(it).inlinedOxsts
            val propertyExpression = inlined.property.expression
            assertThat(propertyExpression)
                .`as`("property expression should be a bare temporal operator, not a cast")
                .isInstanceOf(TemporalOperator::class.java)
            assertThat(inlined.property.eAllOfType<CastExpression>().toList())
                .`as`("every cast should have been removed by the inliner")
                .isEmpty()
            val temporalOperators = inlined.property.eAllOfType<TemporalOperator>().toList()
            assertThat(temporalOperators).hasSize(1)
            assertThat(temporalOperators.single()).isInstanceOf(EF::class.java)
        }
    }

    @Test
    fun `cast on a scalar that the backend can evaluate is removed`() {
        prepare(
            "Case",
            """
                package inlining::tests::cast_scalar
                @VerificationCase
                class Case {
                    var anything: any := 0
                    redefine tran {
                        var number: int := 0
                        number := anything as int
                    }
                    prop {
                        return EF true
                    }
                }
            """,
        ) {
            val inlined = inlineAll(it).inlinedOxsts
            assertThat(inlined.eAllOfType<CastExpression>().toList())
                .`as`("every cast should have been stripped")
                .isEmpty()
        }
    }

    @Test
    fun `a narrowing cast of an instance is erased in a transition and in a property`() {
        assertInlinedModel(
            "Case",
            """
                package inlining::tests::instance_cast
                class Base {
                }
                class Child : Base {
                }
                @VerificationCase
                class Case {
                    contains children: Base[0..*]
                    contains child: Child[1] subsets children
                    var reference: children[0..1] := nothing
                    redefine init {
                        reference := child
                    }
                    redefine tran {
                        var narrowed: Child[0..1] := nothing
                        narrowed := reference as Child
                    }
                    prop {
                        return AG (reference == nothing || (reference as Child) == child)
                    }
                }
            """,
            $$$$"""
                init {
                    root.reference := root.child
                }

                tran {
                    var 'narrowed$$$local0' : inlining::tests::instance_cast::Child[0..1] := nothing
                    'narrowed$$$local0' := root.reference
                }

                prop {
                    AG root.reference == nothing || root.reference == root.child
                }
            """,
        )
    }

    @Test
    fun `widening cast is rejected at inline time`() {
        prepare(
            "Case",
            """
                package inlining::tests::widening_cast
                @VerificationCase
                class Case {
                    var counter: int := 0
                    redefine tran {
                        var anything: any := 0
                        anything := counter as any
                    }
                    prop {
                        return EF true
                    }
                }
            """,
        ) {
            assertThatThrownBy {
                inlineAll(it)
            }.hasMessageContaining("widen")
        }
    }

    @Test
    fun `narrowing cast that respects the lattice is accepted`() {
        prepare(
            "Case",
            """
                package inlining::tests::narrowing_cast
                @VerificationCase
                class Case {
                    var anything: any := 0
                    redefine tran {
                        var number: int := 0
                        number := anything as int
                    }
                    prop {
                        return EF true
                    }
                }
            """,
        ) {
            inlineAll(it)
        }
    }

    @Test
    fun `incorrect narrowing cast is rejected at inline time`() {
        prepare(
            "Case",
            """
                package inlining::tests::widening_cast
                @VerificationCase
                class Case {
                    var counter: int := 0
                    redefine tran {
                        var measurement: real := 0.0
                        counter := measurement as int
                    }
                    prop {
                        return EF true
                    }
                }
            """,
        ) {
            assertThatThrownBy {
                inlineAll(it)
            }.hasMessageContaining("widen")
        }
    }

}
