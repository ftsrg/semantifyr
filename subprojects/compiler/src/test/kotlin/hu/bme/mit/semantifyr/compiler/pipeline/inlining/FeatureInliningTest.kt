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
                    var hits: int := 0
                    tran act() {
                        hits := hits + 1
                    }
                }
                @VerificationCase
                class Host {
                    contains items: Item[0..*]
                    contains first: Item[1] subsets items
                    contains second: Item[1] subsets items
                    contains other: Item[1]
                    redefine tran {
                        inline for (item in items) {
                            inline item.act()
                        }
                    }
                    prop {
                        return AG true
                    }
                }
            """,
        )

        assertThat(main)
            .contains("root.first.hits := root.first.hits + 1")
            .contains("root.second.hits := root.second.hits + 1")
            .doesNotContain("root.other.hits")
    }

    @Test
    fun `a redefinition without subsets stays in the superset of the redefined feature`() {
        val main = inlinedMain(
            "Host",
            """
                package inlining::tests
                class Item {
                    var hits: int := 0
                    tran act() {
                        hits := hits + 1
                    }
                }
                class SpecialItem : Item {
                    var special: int := 0
                    redefine tran act() {
                        special := special + 1
                    }
                }
                class Holder {
                    contains items: Item[0..*]
                    contains first: Item[1] subsets items
                }
                class SpecialHolder : Holder {
                    redefine contains first: SpecialItem[1]
                }
                @VerificationCase
                class Host {
                    contains holder: SpecialHolder[1]
                    redefine tran {
                        inline for (item in holder.items) {
                            inline item.act()
                        }
                    }
                    prop {
                        return AG true
                    }
                }
            """,
        )

        assertThat(main).contains("root.holder.first.special := root.holder.first.special + 1")
    }

    @Test
    fun `a call through a feature redefined under a new name reaches the redefinition`() {
        val main = inlinedMain(
            "Host",
            """
                package inlining::tests
                class Item {
                    var hits: int := 0
                    tran act() {
                        hits := hits + 1
                    }
                }
                class SpecialItem : Item {
                    var special: int := 0
                    redefine tran act() {
                        special := special + 1
                    }
                }
                class Pair {
                    contains original: Item[1]
                    tran run() {
                        inline original.act()
                    }
                }
                class RenamedPair : Pair {
                    contains replacement: SpecialItem[1] redefines original
                }
                @VerificationCase
                class Host {
                    contains pair: RenamedPair[1]
                    redefine tran {
                        inline pair.run()
                    }
                    prop {
                        return AG true
                    }
                }
            """,
        )

        assertThat(main)
            .contains("root.pair.replacement.special := root.pair.replacement.special + 1")
            .doesNotContain("hits")
    }

    @Test
    fun `a reference bound in a nested block reaches the bound instance`() {
        val main = inlinedMain(
            "Host",
            """
                package inlining::tests
                class Lamp {
                    var on: bool := false
                    tran switchOn() {
                        on := true
                    }
                }
                class Switch {
                    refers lamp: Lamp[1]
                    tran press() {
                        inline lamp.switchOn()
                    }
                }
                @VerificationCase
                class Host {
                    contains kitchen: Lamp[1]
                    contains hall: Lamp[1]
                    contains button: Switch[1] {
                        redefine refers lamp: Lamp[1] = kitchen
                    }
                    redefine tran {
                        inline button.press()
                    }
                    prop {
                        return AG true
                    }
                }
            """,
        )

        assertThat(main)
            .contains("root.kitchen.on := true")
            .doesNotContain("root.hall.on")
    }

    @Test
    fun `a nested block rebinds a data feature for its own instance only`() {
        val main = inlinedMain(
            "Host",
            """
                package inlining::tests
                class Limited {
                    refers limit: int = 2
                    var count: int := 0
                    tran step() {
                        assume(count < limit)
                        count := count + 1
                    }
                }
                @VerificationCase
                class Host {
                    contains plain: Limited[1]
                    contains raised: Limited[1] {
                        redefine refers limit: int = 3
                    }
                    redefine tran {
                        inline plain.step()
                        inline raised.step()
                    }
                    prop {
                        return AG true
                    }
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
                    var visits: int := 0
                    derived refers incoming: Edge[0..*] opposite to
                }
                class Edge {
                    var used: bool := false
                    refers to: Node[1] opposite incoming
                }
                @VerificationCase
                class Host {
                    contains firstNode: Node[1]
                    contains secondNode: Node[1]
                    contains firstEdge: Edge[1] {
                        redefine refers to: Node[1] = secondNode
                    }
                    contains secondEdge: Edge[1] {
                        redefine refers to: Node[1] = secondNode
                    }
                    contains thirdEdge: Edge[1] {
                        redefine refers to: Node[1] = firstNode
                    }
                    redefine tran {
                        inline for (edge in secondNode.incoming) {
                            edge.used := true
                        }
                    }
                    prop {
                        return AG true
                    }
                }
            """,
        )

        assertThat(main)
            .contains("root.firstEdge.used := true")
            .contains("root.secondEdge.used := true")
            .doesNotContain("root.thirdEdge.used")
    }

    @Test
    fun `an abstract feature redefined as a containment evaluates to the redefinition`() {
        val main = inlinedMain(
            "Host",
            """
                package inlining::tests
                class Item {
                    var hits: int := 0
                    tran act() {
                        hits := hits + 1
                    }
                }
                abstract class Slot {
                    features payload: Item
                    tran run() {
                        inline payload.act()
                    }
                }
                class FilledSlot : Slot {
                    redefine contains payload: Item[1]
                }
                @VerificationCase
                class Host {
                    contains slot: FilledSlot[1]
                    redefine tran {
                        inline slot.run()
                    }
                    prop {
                        return AG true
                    }
                }
            """,
        )

        assertThat(main).contains("root.slot.payload.hits := root.slot.payload.hits + 1")
    }

    @Test
    fun `an abstract feature filled only by subsetters evaluates to the subsetters`() {
        val main = inlinedMain(
            "Host",
            """
                package inlining::tests
                class Item {
                    var hits: int := 0
                    tran act() {
                        hits := hits + 1
                    }
                }
                @VerificationCase
                class Host {
                    features items: Item[0..*]
                    contains first: Item[1] subsets items
                    contains second: Item[1] subsets items
                    contains other: Item[1]
                    redefine tran {
                        inline for (item in items) {
                            inline item.act()
                        }
                    }
                    prop {
                        return AG true
                    }
                }
            """,
        )

        assertThat(main)
            .contains("root.first.hits := root.first.hits + 1")
            .contains("root.second.hits := root.second.hits + 1")
            .doesNotContain("root.other.hits")
    }

    @Test
    fun `a call through an abstract feature filled by one subsetter reaches it`() {
        val main = inlinedMain(
            "Host",
            """
                package inlining::tests
                class Item {
                    var hits: int := 0
                    tran act() {
                        hits := hits + 1
                    }
                }
                abstract class Slot {
                    features payload: Item[0..1]
                    tran run() {
                        inline payload.act()
                    }
                }
                class FilledSlot : Slot {
                    contains item: Item[1] subsets payload
                }
                @VerificationCase
                class Host {
                    contains slot: FilledSlot[1]
                    redefine tran {
                        inline slot.run()
                    }
                    prop {
                        return AG true
                    }
                }
            """,
        )

        assertThat(main).contains("root.slot.item.hits := root.slot.item.hits + 1")
    }

    @Test
    fun `a global reference resolves to the instance it is bound to`() {
        val main = inlinedMain(
            "Host",
            """
                package inlining::tests
                class Worker {
                    var done: bool := false
                    tran step() {
                        done := true
                    }
                }
                global containment worker : Worker[1]
                global reference alias : Worker[1] = worker
                @VerificationCase
                class Host {
                    redefine tran {
                        inline alias.step()
                    }
                    prop {
                        return AG true
                    }
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
                class Item {
                }
                @VerificationCase
                class Host {
                    contains first: Item[1]
                    contains second: Item[1]
                    refers chosen: Item[1] = second
                    var counter: int := 0
                    redefine tran {
                        inline if (chosen == first) {
                            counter := 1
                        } else {
                            counter := 2
                        }
                    }
                    prop {
                        return AG true
                    }
                }
            """,
        )

        assertThat(main)
            .contains("root.counter := 2")
            .doesNotContain("root.counter := 1")
    }

    @Test
    fun `an inline if comparing an empty instance set with a non-empty one takes its else branch`() {
        val main = inlinedMain(
            "Host",
            """
                package inlining::tests
                class Item {
                }
                @VerificationCase
                class Host {
                    contains first: Item[1]
                    contains absent: Item[0..1]
                    var counter: int := 0
                    redefine tran {
                        inline if (absent == first) {
                            counter := 1
                        } else {
                            counter := 2
                        }
                    }
                    prop {
                        return AG true
                    }
                }
            """,
        )

        assertThat(main)
            .contains("root.counter := 2")
            .doesNotContain("root.counter := 1")
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
