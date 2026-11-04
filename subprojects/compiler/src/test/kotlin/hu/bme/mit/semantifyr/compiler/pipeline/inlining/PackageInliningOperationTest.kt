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
                    var counter : int := 0
                    redefine tran { counter := counter + 1 }
                    prop { return EF counter == 1 }
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
                    var counter : int := 0
                    tran step() { counter := counter + 1 }
                    redefine tran { inline step() }
                    prop { return EF counter == 1 }
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
                    var counter : int := 0
                    tran step() { counter := counter + 1 }
                    redefine tran { inline self.step() }
                    prop { return EF counter == 1 }
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
            """
                package inlining::tests
                class Worker {
                    var done : bool := false
                    tran step() { done := true }
                }
                @VerificationCase
                class Host {
                    contains worker : Worker[1]
                    redefine tran { inline worker.step() }
                    prop { return EF worker.done }
                }
            """,
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
            """
                package inlining::tests
                class Leaf {
                    var value : int := 0
                    tran bump() { value := value + 1 }
                }
                class Middle {
                    contains leaf : Leaf[1]
                }
                @VerificationCase
                class Host {
                    contains middle : Middle[1]
                    redefine tran { inline middle.leaf.bump() }
                    prop { return EF middle.leaf.value == 1 }
                }
            """,
        ) {
            inlineAll(it)
            assertNoInlineOperationsInMain(it)
            assertMainContains(it, "root.middle.leaf.value + 1")
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
                    var counter : int := 0
                    redefine tran someBehavior() { counter := 10 }
                }
                @VerificationCase
                class Testing {
                    contains bases : Base[0..*]
                    contains child : Child[1] subsets bases
                    redefine tran { inline bases.someBehavior() }
                    prop { return EF child.counter == 10 }
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
                class Middle : Base {
                    var middleValue : int := 0
                    redefine tran act() { middleValue := 1 }
                }
                class Leaf : Middle {
                    var leafValue : int := 0
                    redefine tran act() { leafValue := 2 }
                }
                @VerificationCase
                class Testing {
                    contains bases : Base[0..*]
                    contains leaf : Leaf[1] subsets bases
                    redefine tran { inline bases.act() }
                    prop { return EF leaf.leafValue == 2 }
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
                    contains first : Worker[1] subsets workers
                    contains second : Worker[1] subsets workers
                    var current : workers[0..1] := nothing
                    redefine init {
                        choice { current := first } or { current := second }
                    }
                    redefine tran { inline current.step() }
                    prop { return EF (first.done && second.done) }
                }
            """,
        ) {
            inlineAll(it)
            assertNoInlineOperationsInMain(it)
            val mainText = serializer.serializeFormatted(it.inlinedOxsts.mainTransition)
            assertThat(mainText)
                .`as`("dispatch should produce branches referring to both candidates")
                .contains("first")
                .contains("second")
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
                    contains first : Worker[1]
                    contains second : Worker[1]
                    var current : Worker[0..1] := nothing
                    redefine init {
                        choice { current := first } or { current := second }
                    }
                    redefine tran { inline current.step() }
                    prop { return EF (first.done && second.done) }
                }
            """,
        ) {
            inlineAll(it)
            assertNoInlineOperationsInMain(it)
            val mainText = serializer.serializeFormatted(it.inlinedOxsts.mainTransition)
            assertThat(mainText)
                .`as`("class-typed dispatch should guard each branch with an equality to a candidate")
                .contains("first")
                .contains("second")
        }
    }

    @Test
    fun `optional navigation on empty containment collapses the inline call to an empty sequence`() {
        prepare(
            "Host",
            """
                package inlining::tests
                class Leaf {
                    var value : int := 0
                    tran bump() { value := value + 1 }
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
                    var counter : int := 0
                    var base : int := 3
                    tran addBy(amount : int) { counter := counter + amount }
                    redefine tran { inline addBy(base + 2) }
                    prop { return EF counter == 5 }
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
                enum Command { Increment, Decrement }
                @VerificationCase
                class Host {
                    var counter : int := 0
                    tran apply(command : Command) {
                        choice {
                            assume(command == Command::Increment)
                            counter := counter + 1
                        } or {
                            assume(command == Command::Decrement)
                            counter := counter - 1
                        }
                    }
                    redefine tran { inline apply(Command::Increment) }
                    prop { return EF counter == 3 }
                }
            """,
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
            """
                package inlining::tests
                @VerificationCase
                class Host {
                    var counter : int := 0
                    tran step() {
                        var scratch : int := 0
                        scratch := 1
                        counter := counter + scratch
                    }
                    redefine tran {
                        inline step()
                        inline step()
                    }
                    prop { return EF counter == 2 }
                }
            """,
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
            """
                package inlining::tests
                @VerificationCase
                class Host {
                    var left : int := 0
                    var right : int := 0
                    tran set(first : int, second : int) {
                        left := first
                        right := second
                    }
                    redefine tran { inline set(second = 2, first = 1) }
                    prop { return EF left == 1 }
                }
            """,
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
            """
                package inlining::tests
                abstract class Base {
                    abstract tran set(first : int, second : int)
                }
                class Leaf : Base {
                    var left : int := 0
                    var right : int := 0
                    redefine tran set(first : int, second : int) {
                        left := first
                        right := second
                    }
                }
                @VerificationCase
                class Host {
                    contains leaf : Leaf[1]
                    redefine tran { inline leaf.set(second = 2, first = 1) }
                    prop { return EF leaf.left == 1 }
                }
            """,
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
            """
                package inlining::tests
                class Worker { }
                @VerificationCase
                class Host {
                    contains worker : Worker[1]
                    var current : Worker[0..1] := worker
                    tran select(target : Worker[0..1]) {
                        current := target
                    }
                    redefine tran { inline select() }
                    prop { return EF current == nothing }
                }
            """,
        ) {
            inlineAll(it)
            assertMainContains(it, "root.current := nothing")
        }
    }

    @Test
    fun `a required parameter without an argument is a source error`() {
        prepare(
            "Host",
            """
                package inlining::tests
                abstract class Base {
                    abstract tran set()
                }
                class Leaf : Base {
                    var counter : int := 0
                    redefine tran set(value : int) {
                        counter := value
                    }
                }
                @VerificationCase
                class Host {
                    contains leaf : Leaf[1]
                    redefine tran { inline leaf.set() }
                    prop { return EF leaf.counter == 1 }
                }
            """,
        ) {
            assertThatThrownBy { inlineAll(it) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("No argument for the required parameter 'value'")
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
