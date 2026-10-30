/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.pipeline.utils.serializeFormatted
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PackageInliningExpressionTest : InliningTestBase() {
    @Test
    fun `self-call to a named property via bare call`() {
        prepare(
            "Host",
            """
                package inlining::tests::expr_self
                @VerificationCase
                class Host {
                    var x : int := 0
                    prop isPositive() : bool { return x > 0 }
                    prop { return EF isPositive() }
                    redefine tran { x := x + 1 }
                }
            """,
        ) {
            inlineAll(it)
            assertNoPropertyCallsInProperty(it)
            assertPropertyContains(it, "> 0")
        }
    }

    @Test
    fun `self-call to a named property via self navigation`() {
        prepare(
            "Host",
            """
                package inlining::tests::expr_self_nav
                @VerificationCase
                class Host {
                    var x : int := 0
                    prop isPositive() : bool { return x > 0 }
                    prop { return EF self.isPositive() }
                    redefine tran { x := x + 1 }
                }
            """,
        ) {
            inlineAll(it)
            assertNoPropertyCallsInProperty(it)
            assertPropertyContains(it, "> 0")
        }
    }

    @Test
    fun `single-level feature navigation property call`() {
        prepare(
            "Host",
            """
                package inlining::tests::expr_single_nav
                class Worker {
                    var done : bool := false
                    prop isDone() : bool { return done }
                }
                @VerificationCase
                class Host {
                    contains w : Worker[1]
                    redefine tran { w.done := true }
                    prop { return EF w.isDone() }
                }
            """,
        ) {
            inlineAll(it)
            assertNoPropertyCallsInProperty(it)
        }
    }

    @Test
    fun `multi-level feature navigation property call`() {
        prepare(
            "Host",
            """
                package inlining::tests::expr_multi_nav
                class Leaf {
                    var v : int := 0
                    prop isBig() : bool { return v > 10 }
                }
                class Mid {
                    contains leaf : Leaf[1]
                }
                @VerificationCase
                class Host {
                    contains mid : Mid[1]
                    redefine tran { mid.leaf.v := mid.leaf.v + 1 }
                    prop { return EF mid.leaf.isBig() }
                }
            """,
        ) {
            inlineAll(it)
            assertNoPropertyCallsInProperty(it)
        }
    }

    @Test
    fun `polymorphic dispatch via redefining subclass property`() {
        prepare(
            "Testing",
            """
                package inlining::tests::expr_poly
                class Base {
                    prop marker() : bool { return false }
                }
                class Child : Base {
                    var x : int := 0
                    redefine prop marker() : bool { return x == 10 }
                }
                @VerificationCase
                class Testing {
                    contains b : Base[0..*]
                    contains c : Child[1] subsets b
                    redefine tran { c.x := 10 }
                    prop { return EF b.marker() }
                }
            """,
        ) {
            inlineAll(it)
            assertNoPropertyCallsInProperty(it)
            assertPropertyContains(it, "== 10")
        }
    }

    @Test
    fun `multi-level inheritance redefinition picks the leaf property`() {
        prepare(
            "Testing",
            """
                package inlining::tests::expr_multi_inheritance
                class Base {
                    prop marker() : bool { return false }
                }
                class Mid : Base {
                    var m : int := 0
                    redefine prop marker() : bool { return m == 1 }
                }
                class Leaf : Mid {
                    var l : int := 0
                    redefine prop marker() : bool { return l == 2 }
                }
                @VerificationCase
                class Testing {
                    contains b : Base[0..*]
                    contains leaf : Leaf[1] subsets b
                    redefine tran { leaf.l := 2 }
                    prop { return EF b.marker() }
                }
            """,
        ) {
            inlineAll(it)
            assertNoPropertyCallsInProperty(it)
            assertPropertyContains(it, "== 2")
        }
    }

    @Test
    fun `variable dispatch in a property expression expands to an if-then-else chain`() {
        prepare(
            "Dispatcher",
            """
                package inlining::tests::expr_variable_dispatch
                class Worker {
                    var done : bool := false
                    prop isDone() : bool { return done }
                }
                @VerificationCase
                class Dispatcher {
                    contains workers : Worker[0..*]
                    contains a : Worker[1] subsets workers
                    contains b : Worker[1] subsets workers
                    var current : workers[0..1] := nothing
                    redefine init {
                        choice { current := a } or { current := b }
                    }
                    redefine tran { a.done := true }
                    prop { return EF current.isDone() }
                }
            """,
        ) {
            inlineAll(it)
            assertNoPropertyCallsInProperty(it)
            val propertyText = serializer.serializeFormatted(it.inlinedOxsts.property)
            assertThat(propertyText)
                .`as`("dispatch should produce an if-chain touching both candidates")
                .contains("a")
                .contains("b")
        }
    }

    private fun assertNoPropertyCallsInProperty(prepared: Prepared) {
        val remaining = prepared.inlinedOxsts.property
            .eAllOfType<CallSuffixExpression>()
            .toList()
        assertThat(remaining)
            .`as`("expression inliner should eliminate every property CallSuffixExpression from the property")
            .isEmpty()
    }

    private fun assertPropertyContains(
        prepared: Prepared,
        substring: String,
    ) {
        val propertyText = serializer.serializeFormatted(prepared.inlinedOxsts.property)
        assertThat(propertyText)
            .`as`("inlined property should contain '$substring'")
            .contains(substring)
    }
}
