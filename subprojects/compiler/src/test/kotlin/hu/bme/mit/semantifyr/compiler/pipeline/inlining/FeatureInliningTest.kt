/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.compiler.pipeline.utils.serializeFormatted
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FeatureInliningTest : InliningTestBase() {
    @Test
    fun `inline for over a superset unrolls over every subsetter`() {
        val main = inlinedMain(
            "Host",
            """
                package inlining::tests
                class Item {
                    var hits : int := 0
                    tran act() { hits := hits + 1 }
                }
                @VerificationCase
                class Host {
                    contains items : Item[0..*]
                    contains a : Item[1] subsets items
                    contains b : Item[1] subsets items
                    contains other : Item[1]
                    redefine tran {
                        inline for (i in items) {
                            inline i.act()
                        }
                    }
                    prop { return AG true }
                }
            """,
        )

        assertThat(main)
            .contains("root.a.hits := root.a.hits + 1")
            .contains("root.b.hits := root.b.hits + 1")
            .doesNotContain("root.other.hits")
    }

    @Test
    fun `a redefinition without subsets stays in the superset of the redefined feature`() {
        val main = inlinedMain(
            "Host",
            """
                package inlining::tests
                class Item {
                    var hits : int := 0
                    tran act() { hits := hits + 1 }
                }
                class SpecialItem : Item {
                    var special : int := 0
                    redefine tran act() { special := special + 1 }
                }
                class Holder {
                    contains items : Item[0..*]
                    contains first : Item[1] subsets items
                }
                class SpecialHolder : Holder {
                    redefine contains first : SpecialItem[1]
                }
                @VerificationCase
                class Host {
                    contains h : SpecialHolder[1]
                    redefine tran {
                        inline for (i in h.items) {
                            inline i.act()
                        }
                    }
                    prop { return AG true }
                }
            """,
        )

        assertThat(main).contains("root.h.first.special := root.h.first.special + 1")
    }

    @Test
    fun `a call through a feature redefined under a new name reaches the redefinition`() {
        val main = inlinedMain(
            "Host",
            """
                package inlining::tests
                class Item {
                    var hits : int := 0
                    tran act() { hits := hits + 1 }
                }
                class SpecialItem : Item {
                    var special : int := 0
                    redefine tran act() { special := special + 1 }
                }
                class Pair {
                    contains a : Item[1]
                    tran run() { inline a.act() }
                }
                class RenamedPair : Pair {
                    contains newA : SpecialItem[1] redefines a
                }
                @VerificationCase
                class Host {
                    contains p : RenamedPair[1]
                    redefine tran { inline p.run() }
                    prop { return AG true }
                }
            """,
        )

        assertThat(main)
            .contains("root.p.newA.special := root.p.newA.special + 1")
            .doesNotContain("hits")
    }

    @Test
    fun `a nested block rebinds a data feature for its own instance only`() {
        val main = inlinedMain(
            "Host",
            """
                package inlining::tests
                class Limited {
                    refers limit : int = 2
                    var count : int := 0
                    tran step() {
                        assume(count < limit)
                        count := count + 1
                    }
                }
                @VerificationCase
                class Host {
                    contains plain : Limited[1]
                    contains raised : Limited[1] {
                        redefine refers limit : int = 3
                    }
                    redefine tran {
                        inline plain.step()
                        inline raised.step()
                    }
                    prop { return AG true }
                }
            """,
        )

        assertThat(main)
            .contains("root.plain.count < 2")
            .contains("root.raised.count < 3")
    }

    @Test
    fun `a derived reference collects the holders of its opposite`() {
        val main = inlinedMain(
            "Host",
            """
                package inlining::tests
                class Node {
                    var visits : int := 0
                    derived refers incoming : Edge[0..*] opposite to
                }
                class Edge {
                    var used : bool := false
                    refers to : Node[1] opposite incoming
                }
                @VerificationCase
                class Host {
                    contains n1 : Node[1]
                    contains n2 : Node[1]
                    contains e1 : Edge[1] {
                        redefine refers to : Node[1] = n2
                    }
                    contains e2 : Edge[1] {
                        redefine refers to : Node[1] = n2
                    }
                    contains e3 : Edge[1] {
                        redefine refers to : Node[1] = n1
                    }
                    redefine tran {
                        inline for (e in n2.incoming) {
                            e.used := true
                        }
                    }
                    prop { return AG true }
                }
            """,
        )

        assertThat(main)
            .contains("root.e1.used := true")
            .contains("root.e2.used := true")
            .doesNotContain("root.e3.used")
    }

    @Test
    fun `an abstract feature redefined as a containment evaluates to the redefinition`() {
        val main = inlinedMain(
            "Host",
            """
                package inlining::tests
                class Item {
                    var hits : int := 0
                    tran act() { hits := hits + 1 }
                }
                abstract class Slot {
                    features payload : Item
                    tran run() { inline payload.act() }
                }
                class FilledSlot : Slot {
                    redefine contains payload : Item[1]
                }
                @VerificationCase
                class Host {
                    contains s : FilledSlot[1]
                    redefine tran { inline s.run() }
                    prop { return AG true }
                }
            """,
        )

        assertThat(main).contains("root.s.payload.hits := root.s.payload.hits + 1")
    }

    @Test
    fun `a global reference resolves to the instance it is bound to`() {
        val main = inlinedMain(
            "Host",
            """
                package inlining::tests
                class Worker {
                    var done : bool := false
                    tran step() { done := true }
                }
                global containment worker : Worker[1]
                global reference alias : Worker[1] = worker
                @VerificationCase
                class Host {
                    redefine tran { inline alias.step() }
                    prop { return AG true }
                }
            """,
        )

        assertThat(main)
            .contains("worker.done := true")
            .doesNotContain("alias")
    }

    @Test
    fun `an inline if comparing instances selects its branch statically`() {
        val main = inlinedMain(
            "Host",
            """
                package inlining::tests
                class Item { }
                @VerificationCase
                class Host {
                    contains a : Item[1]
                    contains b : Item[1]
                    refers chosen : Item[1] = b
                    var x : int := 0
                    redefine tran {
                        inline if (chosen == a) {
                            x := 1
                        } else {
                            x := 2
                        }
                    }
                    prop { return AG true }
                }
            """,
        )

        assertThat(main)
            .contains("root.x := 2")
            .doesNotContain("root.x := 1")
    }

    private fun inlinedMain(
        className: String,
        source: String,
    ): String {
        return prepare(className, source) {
            inlineAll(it)
            serializer.serializeFormatted(it.inlinedOxsts.mainTransition)
        }
    }
}
