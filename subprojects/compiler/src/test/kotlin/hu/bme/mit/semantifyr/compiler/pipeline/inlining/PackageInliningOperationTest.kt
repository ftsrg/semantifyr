/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.pipeline.utils.serializeFormatted
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineOperation
import org.assertj.core.api.Assertions.assertThat
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
                    var x : int := 0
                    redefine tran { x := x + 1 }
                    prop { return EF x == 1 }
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
                    var x : int := 0
                    tran step() { x := x + 1 }
                    redefine tran { inline step() }
                    prop { return EF x == 1 }
                }
            """,
        ) {
            inlineAll(it)
            assertNoInlineOperationsInMain(it)
            assertMainContains(it, "root.x + 1")
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
                    var x : int := 0
                    tran step() { x := x + 1 }
                    redefine tran { inline self.step() }
                    prop { return EF x == 1 }
                }
            """,
        ) {
            inlineAll(it)
            assertNoInlineOperationsInMain(it)
            assertMainContains(it, "root.x + 1")
        }
    }

    @Test
    fun `single-level feature navigation call`() {
        prepare(
            "Host",
            """
                package inlining::tests
                class Worker {
                    var done : bool := false
                    tran step() { done := true }
                }
                @VerificationCase
                class Host {
                    contains w : Worker[1]
                    redefine tran { inline w.step() }
                    prop { return EF w.done }
                }
            """,
        ) {
            inlineAll(it)
            assertNoInlineOperationsInMain(it)
            assertMainContains(it, "root.w.done := true")
        }
    }

    @Test
    fun `multi-level feature navigation call`() {
        prepare(
            "Host",
            """
                package inlining::tests
                class Leaf {
                    var v : int := 0
                    tran bump() { v := v + 1 }
                }
                class Mid {
                    contains leaf : Leaf[1]
                }
                @VerificationCase
                class Host {
                    contains mid : Mid[1]
                    redefine tran { inline mid.leaf.bump() }
                    prop { return EF mid.leaf.v == 1 }
                }
            """,
        ) {
            inlineAll(it)
            assertNoInlineOperationsInMain(it)
            assertMainContains(it, "root.mid.leaf.v + 1")
        }
    }

    @Test
    fun `polymorphic dispatch via redefining subclass and base-typed feature`() {
        prepare(
            "Testing",
            """
                package inlining::tests
                class Base {
                    tran someBehavior() { }
                }
                class Child : Base {
                    var x : int := 0
                    redefine tran someBehavior() { x := 10 }
                }
                @VerificationCase
                class Testing {
                    contains b : Base[0..*]
                    contains c : Child[1] subsets b
                    redefine tran { inline b.someBehavior() }
                    prop { return EF c.x == 10 }
                }
            """,
        ) {
            inlineAll(it)
            assertNoInlineOperationsInMain(it)

            assertMainContains(it, ":= 10")
        }
    }

    @Test
    fun `multi-level inheritance redefinition picks the leaf transition`() {
        prepare(
            "Testing",
            """
                package inlining::tests
                class Base {
                    tran act() { }
                }
                class Mid : Base {
                    var m : int := 0
                    redefine tran act() { m := 1 }
                }
                class Leaf : Mid {
                    var l : int := 0
                    redefine tran act() { l := 2 }
                }
                @VerificationCase
                class Testing {
                    contains b : Base[0..*]
                    contains leaf : Leaf[1] subsets b
                    redefine tran { inline b.act() }
                    prop { return EF leaf.l == 2 }
                }
            """,
        ) {
            inlineAll(it)
            assertNoInlineOperationsInMain(it)

            assertMainContains(it, ":= 2")
        }
    }

    @Test
    fun `variable dispatch over a feature-typed variable expands to choice over candidates`() {
        prepare(
            "Dispatcher",
            """
                package inlining::tests
                class Worker {
                    var done : bool := false
                    tran step() { done := true }
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
                    redefine tran { inline current.step() }
                    prop { return EF (a.done && b.done) }
                }
            """,
        ) {
            inlineAll(it)
            assertNoInlineOperationsInMain(it)
            val mainText = serializer.serializeFormatted(it.inlinedOxsts.mainTransition)
            assertThat(mainText)
                .`as`("dispatch should produce branches referring to both candidates")
                .contains("a")
                .contains("b")
        }
    }

    @Test
    fun `dispatch over a class-typed variable fans out across every instance of that class`() {
        prepare(
            "Dispatcher",
            """
                package inlining::tests
                class Worker {
                    var done : bool := false
                    tran step() { done := true }
                }
                @VerificationCase
                class Dispatcher {
                    contains a : Worker[1]
                    contains b : Worker[1]
                    var current : Worker[0..1] := nothing
                    redefine init {
                        choice { current := a } or { current := b }
                    }
                    redefine tran { inline current.step() }
                    prop { return EF (a.done && b.done) }
                }
            """,
        ) {
            inlineAll(it)
            assertNoInlineOperationsInMain(it)
            val mainText = serializer.serializeFormatted(it.inlinedOxsts.mainTransition)
            assertThat(mainText)
                .`as`("class-typed dispatch should guard each branch with an equality to a candidate")
                .contains("a")
                .contains("b")
        }
    }

    @Test
    fun `optional navigation on empty containment collapses the inline call to an empty sequence`() {
        prepare(
            "Host",
            """
                package inlining::tests
                class Leaf {
                    var v : int := 0
                    tran bump() { v := v + 1 }
                }
                @VerificationCase
                class Host {
                    contains leaf : Leaf[0..1]
                    redefine tran { inline leaf?.bump() }
                    prop { return AG true }
                }
            """,
        ) {
            inlineAll(it)
            assertNoInlineOperationsInMain(it)

            val mainText = serializer.serializeFormatted(it.inlinedOxsts.mainTransition)
            assertThat(mainText)
                .`as`("optional navigation on empty container should produce an empty main body")
                .doesNotContain("bump")
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
                    var x : int := 0
                    var base : int := 3
                    tran addBy(n : int) { x := x + n }
                    redefine tran { inline addBy(base + 2) }
                    prop { return EF x == 5 }
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
            """
                package inlining::tests
                enum Command { Inc, Dec }
                @VerificationCase
                class Host {
                    var x : int := 0
                    tran apply(cmd : Command) {
                        choice {
                            assume(cmd == Command::Inc)
                            x := x + 1
                        } or {
                            assume(cmd == Command::Dec)
                            x := x - 1
                        }
                    }
                    redefine tran { inline apply(Command::Inc) }
                    prop { return EF x == 3 }
                }
            """,
        ) {
            inlineAll(it)
            assertNoInlineOperationsInMain(it)
            val mainText = serializer.serializeFormatted(it.inlinedOxsts.mainTransition)
            assertThat(mainText)
                .`as`("enum argument must be substituted, leaving only the Inc-branch increment")
                .contains("x := root.x + 1")
                .doesNotContain("- 1")
        }
    }

    @Test
    fun `nested inline calls declaring local vars of the same name avoid collision`() {
        prepare(
            "Host",
            """
                package inlining::tests
                @VerificationCase
                class Host {
                    var x : int := 0
                    tran step() {
                        var tmp : int := 0
                        tmp := 1
                        x := x + tmp
                    }
                    redefine tran {
                        inline step()
                        inline step()
                    }
                    prop { return EF x == 2 }
                }
            """,
        ) {
            inlineAll(it)
            assertNoInlineOperationsInMain(it)
            val mainText = serializer.serializeFormatted(it.inlinedOxsts.mainTransition)
            val localDeclarationMatches = Regex("""var\s+'?tmp[^\s:']*'?\s*:""").findAll(mainText).toList()
            val distinctNames = localDeclarationMatches.map {
                it.value
            }.toSet()
            assertThat(localDeclarationMatches)
                .`as`("expected two local tmp declarations after two inline expansions, got main body: $mainText")
                .hasSize(2)
            assertThat(distinctNames)
                .`as`("the two inlined tmp declarations must be renamed apart: $distinctNames")
                .hasSize(2)
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
