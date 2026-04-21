/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineOperation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Operation-level inlining tests against full OXSTS packages - self-calls,
 * navigation calls, polymorphism / redefinition, multi-level navigation,
 * multi-level inheritance, and variable dispatching.
 *
 * Each test compiles a small package via [PackageExpanderTestBase.prepare]
 * and drives the full inliner. "Inlining succeeded" means the main
 * transition contains no residual [InlineOperation] nodes. Where the test
 * also cares about the specific body picked, it asserts on the serialized
 * text of the inlined main transition.
 */
class PackageInliningOperationTest : PackageExpanderTestBase() {

    @Test
    fun `self-call to main via bare inline main`() {
        val prepared = prepare(
            "Host",
            """
                package inlining::tests::self_main
                @VerificationCase
                class Host {
                    var x : int := 0
                    redefine tran { x := x + 1 }
                    prop { return EF x == 1 }
                }
            """,
        )
        inlineAll(prepared)
        assertNoInlineOperationsInMain(prepared)
    }

    @Test
    fun `self-call to a named tran via bare call`() {
        val prepared = prepare(
            "Host",
            """
                package inlining::tests::self_named
                @VerificationCase
                class Host {
                    var x : int := 0
                    tran step() { x := x + 1 }
                    redefine tran { inline step() }
                    prop { return EF x == 1 }
                }
            """,
        )
        inlineAll(prepared)
        assertNoInlineOperationsInMain(prepared)
        // Body is rewritten through the instance tree: `x` becomes `root.x`.
        assertMainContains(prepared, "root.x + 1")
    }

    @Test
    fun `self-call to a named tran via self navigation`() {
        val prepared = prepare(
            "Host",
            """
                package inlining::tests::self_nav
                @VerificationCase
                class Host {
                    var x : int := 0
                    tran step() { x := x + 1 }
                    redefine tran { inline self.step() }
                    prop { return EF x == 1 }
                }
            """,
        )
        inlineAll(prepared)
        assertNoInlineOperationsInMain(prepared)
        assertMainContains(prepared, "root.x + 1")
    }

    @Test
    fun `single-level feature navigation call`() {
        val prepared = prepare(
            "Host",
            """
                package inlining::tests::single_nav
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
        )
        inlineAll(prepared)
        assertNoInlineOperationsInMain(prepared)
        assertMainContains(prepared, "root.w.done := true")
    }

    @Test
    fun `multi-level feature navigation call`() {
        val prepared = prepare(
            "Host",
            """
                package inlining::tests::multi_nav
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
        )
        inlineAll(prepared)
        assertNoInlineOperationsInMain(prepared)
        assertMainContains(prepared, "root.mid.leaf.v + 1")
    }

    @Test
    fun `polymorphic dispatch via redefining subclass and base-typed feature`() {
        val prepared = prepare(
            "Testing",
            """
                package inlining::tests::poly
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
        )
        inlineAll(prepared)
        assertNoInlineOperationsInMain(prepared)
        // The Child redefinition should win; the assignment `x := 10` must
        // appear in the inlined main, proving the redefinition-aware resolver
        // picked up the subclass body rather than the empty Base body.
        assertMainContains(prepared, ":= 10")
    }

    @Test
    fun `multi-level inheritance redefinition picks the leaf transition`() {
        val prepared = prepare(
            "Testing",
            """
                package inlining::tests::multi_inheritance
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
        )
        inlineAll(prepared)
        assertNoInlineOperationsInMain(prepared)
        // Must pick the leaf redefinition, not Mid's or Base's.
        assertMainContains(prepared, ":= 2")
    }

    @Test
    fun `variable dispatch over a feature-typed variable expands to choice over candidates`() {
        val prepared = prepare(
            "Dispatcher",
            """
                package inlining::tests::variable_dispatch
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
        )
        inlineAll(prepared)
        assertNoInlineOperationsInMain(prepared)
        // Both candidates should appear: the dispatch fans out across a and b.
        val mainText = serializeNormalized(prepared.inlinedOxsts.mainTransition)
        assertThat(mainText)
            .`as`("dispatch should produce branches referring to both candidates")
            .contains("a")
            .contains("b")
    }

    @Test
    fun `dispatch over a class-typed variable fans out across every instance of that class`() {
        val prepared = prepare(
            "Dispatcher",
            """
                package inlining::tests::class_dispatch
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
        )
        inlineAll(prepared)
        assertNoInlineOperationsInMain(prepared)
        // Class-typed variable dispatch goes through InstanceCollector.instancesOfType;
        // the dispatch should fan out to both a and b (instances of Worker).
        val mainText = serializeNormalized(prepared.inlinedOxsts.mainTransition)
        assertThat(mainText)
            .`as`("class-typed dispatch should guard each branch with an equality to a candidate")
            .contains("a")
            .contains("b")
    }

    @Test
    fun `optional navigation on empty containment collapses the inline call to an empty sequence`() {
        val prepared = prepare(
            "Host",
            """
                package inlining::tests::optional_nav
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
        )
        inlineAll(prepared)
        assertNoInlineOperationsInMain(prepared)
        // With multiplicity [0..1] and no subsetter, `leaf` resolves to no
        // instance; the optional navigation drops the inline call, leaving
        // the main body free of any `leaf` or `bump` references.
        val mainText = serializeNormalized(prepared.inlinedOxsts.mainTransition)
        assertThat(mainText)
            .`as`("optional navigation on empty container should produce an empty main body")
            .doesNotContain("bump")
    }

    @Test
    fun `parametric inline call substitutes a non-literal argument into the body`() {
        val prepared = prepare(
            "Host",
            """
                package inlining::tests::non_literal_arg
                @VerificationCase
                class Host {
                    var x : int := 0
                    var base : int := 3
                    tran addBy(n : int) { x := x + n }
                    redefine tran { inline addBy(base + 2) }
                    prop { return EF x == 5 }
                }
            """,
        )
        inlineAll(prepared)
        assertNoInlineOperationsInMain(prepared)
        val mainText = serializeNormalized(prepared.inlinedOxsts.mainTransition)
        assertThat(mainText)
            .`as`("inlined body must carry the caller's expression in place of the parameter")
            .contains("base + 2")
    }

    @Test
    fun `parametric inline call substitutes an enum argument into the body`() {
        val prepared = prepare(
            "Host",
            """
                package inlining::tests::enum_arg
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
        )
        inlineAll(prepared)
        assertNoInlineOperationsInMain(prepared)
        val mainText = serializeNormalized(prepared.inlinedOxsts.mainTransition)
        assertThat(mainText)
            .`as`("enum argument must be substituted into the inlined assumption")
            .contains("Command::Inc")
    }

    @Test
    fun `nested inline calls declaring local vars of the same name avoid collision`() {
        val prepared = prepare(
            "Host",
            """
                package inlining::tests::local_var_collision
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
        )
        inlineAll(prepared)
        assertNoInlineOperationsInMain(prepared)
        val mainText = serializeNormalized(prepared.inlinedOxsts.mainTransition)
        // Two inlined copies of `step` means two local `tmp` declarations in
        // the same scope; they must have been renamed apart by the expander.
        // The renamer introduces characters that the serializer escapes with
        // single quotes, so match against either form.
        val localDeclarationMatches = Regex("""var\s+'?tmp[^\s:']*'?\s*:""").findAll(mainText).toList()
        val distinctNames = localDeclarationMatches.map { it.value }.toSet()
        assertThat(localDeclarationMatches)
            .`as`("expected two local tmp declarations after two inline expansions, got main body: $mainText")
            .hasSize(2)
        assertThat(distinctNames)
            .`as`("the two inlined tmp declarations must be renamed apart: $distinctNames")
            .hasSize(2)
    }

    private fun assertNoInlineOperationsInMain(prepared: Prepared) {
        val remaining = prepared.inlinedOxsts.mainTransition.eAllOfType<InlineOperation>().toList()
        assertThat(remaining)
            .`as`("inliner should eliminate every InlineOperation from the main transition")
            .isEmpty()
    }

    private fun assertMainContains(prepared: Prepared, substring: String) {
        val mainText = serializeNormalized(prepared.inlinedOxsts.mainTransition)
        assertThat(mainText)
            .`as`("inlined main transition should contain '$substring'")
            .contains(substring)
    }
}
