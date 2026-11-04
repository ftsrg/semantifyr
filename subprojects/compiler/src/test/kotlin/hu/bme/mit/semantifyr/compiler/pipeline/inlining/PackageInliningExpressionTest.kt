/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.pipeline.utils.serializeFormatted
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RecordLiteral
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
                    var counter : int := 0
                    prop isPositive() : bool { return counter > 0 }
                    prop { return EF isPositive() }
                    redefine tran { counter := counter + 1 }
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
                    var counter : int := 0
                    prop isPositive() : bool { return counter > 0 }
                    prop { return EF self.isPositive() }
                    redefine tran { counter := counter + 1 }
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
                    contains worker : Worker[1]
                    redefine tran { worker.done := true }
                    prop { return EF worker.isDone() }
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
                    var value : int := 0
                    prop isBig() : bool { return value > 10 }
                }
                class Middle {
                    contains leaf : Leaf[1]
                }
                @VerificationCase
                class Host {
                    contains middle : Middle[1]
                    redefine tran { middle.leaf.value := middle.leaf.value + 1 }
                    prop { return EF middle.leaf.isBig() }
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
                    var counter : int := 0
                    redefine prop marker() : bool { return counter == 10 }
                }
                @VerificationCase
                class Testing {
                    contains bases : Base[0..*]
                    contains child : Child[1] subsets bases
                    redefine tran { child.counter := 10 }
                    prop { return EF bases.marker() }
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
                class Middle : Base {
                    var middleValue : int := 0
                    redefine prop marker() : bool { return middleValue == 1 }
                }
                class Leaf : Middle {
                    var leafValue : int := 0
                    redefine prop marker() : bool { return leafValue == 2 }
                }
                @VerificationCase
                class Testing {
                    contains bases : Base[0..*]
                    contains leaf : Leaf[1] subsets bases
                    redefine tran { leaf.leafValue := 2 }
                    prop { return EF bases.marker() }
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
                    contains first : Worker[1] subsets workers
                    contains second : Worker[1] subsets workers
                    var current : workers[0..1] := nothing
                    redefine init {
                        choice { current := first } or { current := second }
                    }
                    redefine tran { first.done := true }
                    prop { return EF current.isDone() }
                }
            """,
        ) {
            inlineAll(it)
            assertNoPropertyCallsInProperty(it)
            val propertyText = serializer.serializeFormatted(it.inlinedOxsts.property)
            assertThat(propertyText)
                .`as`("dispatch should produce an if-chain touching both candidates")
                .contains("first")
                .contains("second")
        }
    }

    @Test
    fun `named arguments of a property call bind by name when out of declaration order`() {
        prepare(
            "Host",
            """
                package inlining::tests
                @VerificationCase
                class Host {
                    var expected : int := 0
                    var measured : int := 0
                    prop difference(minuend : int, subtrahend : int) : int {
                        return minuend - subtrahend
                    }
                    prop { return EF expected == difference(subtrahend = 1, minuend = measured) }
                }
            """,
        ) {
            inlineAll(it)
            assertNoPropertyCallsInProperty(it)
            assertPropertyContains(it, "root.measured - 1")
        }
    }

    @Test
    fun `a record literal survives inlining with its calls and arguments inlined`() {
        prepare(
            "Host",
            """
                package inlining::tests::record_literal
                record Point {
                    var x: int
                    var y: int := 0
                }
                @VerificationCase
                class Host {
                    var position: Point
                    prop next(): int { return position.x + 1 }
                    tran moveTo(target: Point) { position := target }
                    redefine tran { inline moveTo(Point { x = next() }) }
                    prop { return AG position != Point { x = next(), y = 2 } }
                }
            """,
        ) {
            inlineAll(it)
            assertNoPropertyCallsInProperty(it)
            val main = it.inlinedOxsts.mainTransition
            assertThat(main.eAllOfType<CallSuffixExpression>().toList()).isEmpty()
            assertThat(main.eAllOfType<RecordLiteral>().toList()).hasSize(1)
            val mainText = serializer.serializeFormatted(main)
            assertThat(mainText).contains(":= inlining::tests::record_literal::Point { x = root.position.x + 1 }")
            assertPropertyContains(it, ", y = 2 }")
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
