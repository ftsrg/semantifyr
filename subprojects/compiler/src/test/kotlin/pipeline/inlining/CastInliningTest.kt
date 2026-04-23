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

class CastInliningTest : PackageExpanderTestBase() {

    @Test
    fun `cast around a temporal operator at the property level is removed during inlining`() {
        val prepared = prepare(
            "Case",
            """
                package inlining::tests::cast_in_property
                @VerificationCase
                class Case {
                    var x: int := 0
                    redefine tran { x := x + 1 }
                    prop { return (EF x == 5) as bool }
                }
            """,
        )
        val inlined = inlineAll(prepared).inlinedOxsts
        val propertyExpression = inlined.property.expression
        assertThat(propertyExpression)
            .`as`("property expression should be a bare temporal operator, not a cast")
            .isInstanceOf(TemporalOperator::class.java)
        // No leftover cast anywhere in the property tree.
        assertThat(inlined.property.eAllOfType<CastExpression>().toList())
            .`as`("every cast should have been removed by the inliner")
            .isEmpty()
        // Only ONE temporal operator: the original EF. If the
        // ensureTemporalExpressions step injected an AG around a cast we'd
        // see two operators here.
        val temporalOperators = inlined.property.eAllOfType<TemporalOperator>().toList()
        assertThat(temporalOperators).hasSize(1)
        assertThat(temporalOperators.single()).isInstanceOf(EF::class.java)
    }

    @Test
    fun `cast on a scalar that the backend can evaluate is removed`() {
        val prepared = prepare(
            "Case",
            """
                package inlining::tests::cast_scalar
                @VerificationCase
                class Case {
                    var a: any := 0
                    redefine tran {
                        var n: int := 0
                        n := a as int
                    }
                    prop { return EF true }
                }
            """,
        )
        val inlined = inlineAll(prepared).inlinedOxsts
        assertThat(inlined.eAllOfType<CastExpression>().toList())
            .`as`("every cast should have been stripped")
            .isEmpty()
    }

    @Test
    fun `widening cast is rejected at inline time`() {
        val prepared = prepare(
            "Case",
            """
                package inlining::tests::widening_cast
                @VerificationCase
                class Case {
                    var x: int := 0
                    redefine tran {
                        var a: any := 0
                        a := x as any
                    }
                    prop { return EF true }
                }
            """,
        )
        assertThatThrownBy {
            inlineAll(prepared)
        }.hasMessageContaining("widen")
    }

    @Test
    fun `narrowing cast that respects the lattice is accepted`() {
        val prepared = prepare(
            "Case",
            """
                package inlining::tests::narrowing_cast
                @VerificationCase
                class Case {
                    var a: any := 0
                    redefine tran {
                        var n: int := 0
                        n := a as int
                    }
                    prop { return EF true }
                }
            """,
        )
        inlineAll(prepared)
    }
}
