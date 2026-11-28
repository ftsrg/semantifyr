/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.pipeline.utils.serializeFormatted
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.TestModels.testModel
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
                    var counter: int := 0
                    prop isPositive(): bool {
                        return counter > 0
                    }
                    redefine prop {
                        return EF isPositive()
                    }
                    redefine tran {
                        counter := counter + 1
                    }
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
                    var counter: int := 0
                    prop isPositive(): bool {
                        return counter > 0
                    }
                    redefine prop {
                        return EF self.isPositive()
                    }
                    redefine tran {
                        counter := counter + 1
                    }
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
            testModel(javaClass, "single_level_navigation.semantifyr"),
        ) {
            inlineAll(it)
            assertNoPropertyCallsInProperty(it)
        }
    }

    @Test
    fun `multi-level feature navigation property call`() {
        prepare(
            "Host",
            testModel(javaClass, "multi_level_navigation.semantifyr"),
        ) {
            inlineAll(it)
            assertNoPropertyCallsInProperty(it)
        }
    }

    @Test
    fun `polymorphic dispatch via redefining subclass property`() {
        prepare(
            "Testing",
            testModel(javaClass, "polymorphic_dispatch.semantifyr"),
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
            testModel(javaClass, "multi_level_inheritance.semantifyr"),
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
            testModel(javaClass, "variable_dispatch.semantifyr"),
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
                    var expected: int := 0
                    var measured: int := 0
                    prop difference(minuend: int, subtrahend: int): int {
                        return minuend - subtrahend
                    }
                    redefine prop {
                        return EF expected == difference(subtrahend = 1, minuend = measured)
                    }
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
            testModel(javaClass, "record_literal.semantifyr"),
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
