/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.pipeline.utils.serializeFormatted
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.TestModels.testModel
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineOperation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PackageInliningOperationTest : InliningTestBase() {
    @Test
    fun `call to main via bare inline main`() {
        prepare(
            "Host",
            """
                package inlining::tests
                @VerificationCase
                class Host {
                    var counter: int := 0
                    redefine tran {
                        counter := counter + 1
                    }
                    redefine prop {
                        return EF counter == 1
                    }
                }
            """,
        ) {
            inlineAll(it)
            assertNoInlineOperationsInMain(it)
        }
    }

    @Test
    fun `call to a named tran via bare call`() {
        prepare(
            "Host",
            """
                package inlining::tests
                @VerificationCase
                class Host {
                    var counter: int := 0
                    tran step() {
                        counter := counter + 1
                    }
                    redefine tran {
                        inline step()
                    }
                    redefine prop {
                        return EF counter == 1
                    }
                }
            """,
        ) {
            inlineAll(it)
            assertNoInlineOperationsInMain(it)
            assertMainContains(it, "root.counter + 1")
        }
    }

    @Test
    fun `call to a named tran via self navigation`() {
        prepare(
            "Host",
            """
                package inlining::tests
                @VerificationCase
                class Host {
                    var counter: int := 0
                    tran step() {
                        counter := counter + 1
                    }
                    redefine tran {
                        inline self.step()
                    }
                    redefine prop {
                        return EF counter == 1
                    }
                }
            """,
        ) {
            inlineAll(it)
            assertNoInlineOperationsInMain(it)
            assertMainContains(it, "root.counter + 1")
        }
    }

    @Test
    fun `single-level feature navigation call`() {
        prepare(
            "Host",
            testModel(javaClass, "single_level_navigation.semantifyr"),
        ) {
            inlineAll(it)
            assertNoInlineOperationsInMain(it)
            assertMainContains(it, "root.worker.done := true")
        }
    }

    @Test
    fun `multi-level feature navigation call`() {
        prepare(
            "Host",
            testModel(javaClass, "multi_level_navigation.semantifyr"),
        ) {
            inlineAll(it)
            assertNoInlineOperationsInMain(it)
            assertMainContains(it, "root.middle.leaf.value + 1")
        }
    }

    @Test
    fun `multi-level inheritance redefinition picks the leaf transition`() {
        prepare(
            "Testing",
            testModel(javaClass, "multi_level_inheritance.semantifyr"),
        ) {
            inlineAll(it)
            assertNoInlineOperationsInMain(it)

            assertMainContains(it, ":= 2")
        }
    }

    @Test
    fun `parametric inline call substitutes a non-literal argument into the body`() {
        prepare(
            "Host",
            """
                package inlining::tests
                @VerificationCase
                class Host {
                    var counter: int := 0
                    var base: int := 3
                    tran addBy(amount: int) {
                        counter := counter + amount
                    }
                    redefine tran {
                        inline addBy(base + 2)
                    }
                    redefine prop {
                        return EF counter == 5
                    }
                }
            """,
        ) {
            inlineAll(it)
            assertNoInlineOperationsInMain(it)
            val mainText = serializer.serializeFormatted(it.inlinedOxsts.mainTransition)
            assertThat(mainText)
                .`as`("inlined body must carry the caller's expression in place of the parameter")
                .contains("base + 2")
        }
    }

    @Test
    fun `parametric inline call substitutes an enum argument into the body`() {
        prepare(
            "Host",
            testModel(javaClass, "enum_argument.semantifyr"),
        ) {
            inlineAll(it)
            assertNoInlineOperationsInMain(it)
            val mainText = serializer.serializeFormatted(it.inlinedOxsts.mainTransition)
            assertThat(mainText)
                .`as`("enum argument must be substituted, leaving only the Increment-branch increment")
                .contains("counter := root.counter + 1")
                .doesNotContain("- 1")
        }
    }

    @Test
    fun `nested inline calls declaring local variables of the same name avoid collision`() {
        prepare(
            "Host",
            testModel(javaClass, "nested_local_variables.semantifyr"),
        ) {
            inlineAll(it)
            assertNoInlineOperationsInMain(it)
            val mainText = serializer.serializeFormatted(it.inlinedOxsts.mainTransition)
            val localDeclarationMatches = Regex("""var\s+'?scratch[^\s:']*'?\s*:""").findAll(mainText).toList()
            val distinctNames = localDeclarationMatches.map {
                it.value
            }.toSet()
            assertThat(localDeclarationMatches)
                .`as`("expected two local scratch declarations after two inline expansions, got main body: $mainText")
                .hasSize(2)
            assertThat(distinctNames)
                .`as`("the two inlined scratch declarations must be renamed apart: $distinctNames")
                .hasSize(2)
        }
    }

    @Test
    fun `named arguments bind by name when out of declaration order`() {
        prepare(
            "Host",
            testModel(javaClass, "named_arguments_out_of_order.semantifyr"),
        ) {
            inlineAll(it)
            assertMainContains(it, "root.left := 1")
            assertMainContains(it, "root.right := 2")
        }
    }

    @Test
    fun `named arguments bind by name in a redefinition`() {
        prepare(
            "Host",
            testModel(javaClass, "named_arguments_in_redefinition.semantifyr"),
        ) {
            inlineAll(it)
            assertMainContains(it, "root.leaf.left := 1")
            assertMainContains(it, "root.leaf.right := 2")
        }
    }

    @Test
    fun `an omitted optional argument binds nothing`() {
        prepare(
            "Host",
            testModel(javaClass, "omitted_optional_argument.semantifyr"),
        ) {
            inlineAll(it)
            assertMainContains(it, "root.current := nothing")
        }
    }

    @Test
    fun `a required parameter without an argument is a source error`() {
        prepare(
            "Host",
            testModel(javaClass, "missing_required_argument.semantifyr"),
        ) {
            assertThatThrownBy { inlineAll(it) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Missing an argument for the required parameter 'value'")
        }
    }

    private fun assertNoInlineOperationsInMain(prepared: Prepared) {
        val remaining = prepared.inlinedOxsts.mainTransition
            .eAllOfType<InlineOperation>()
            .toList()
        assertThat(remaining)
            .`as`("inliner should eliminate every InlineOperation from the main transition")
            .isEmpty()
    }

    private fun assertMainContains(
        prepared: Prepared,
        substring: String,
    ) {
        val mainText = serializer.serializeFormatted(prepared.inlinedOxsts.mainTransition)
        assertThat(mainText)
            .`as`("inlined main transition should contain '$substring'")
            .contains(substring)
    }
}
