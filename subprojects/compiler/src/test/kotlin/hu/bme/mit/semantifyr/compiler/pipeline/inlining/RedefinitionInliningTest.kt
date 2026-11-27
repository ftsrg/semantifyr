/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import org.junit.jupiter.api.Test

class RedefinitionInliningTest : InliningTestBase() {

    private val hierarchy = """
        package inlining::tests
        class Base {
            tran act() {
            }
        }
        class Child : Base {
            var count: int := 0
            redefine tran act() {
                count := 10
            }
        }
        class Middle : Base {
            var middleCount: int := 0
            redefine tran act() {
                middleCount := 1
            }
        }
        class Leaf : Middle {
            var leafCount: int := 0
            redefine tran act() {
                leafCount := 2
            }
        }
    """

    @Test
    fun `an init, tran and prop without redefine leave the ones of Anything in the case`() {
        assertInlinedModel(
            "Host",
            """
                package inlining::tests
                @VerificationCase
                class Host {
                    var counter: int := 0
                    init {
                        counter := 1
                    }
                    tran {
                        counter := counter + 1
                    }
                    prop {
                        return EF counter == 5
                    }
                }
            """,
            """
                init {
                }

                tran {
                }

                prop {
                    AG true
                }
            """,
        )
    }

    @Test
    fun `a tran without redefine leaves the redefinition of the parent class in the step`() {
        assertInlinedModel(
            "Host",
            """
                package inlining::tests
                class Base {
                    var counter: int := 0
                    redefine tran {
                        counter := 1
                    }
                    redefine prop {
                        return EF counter == 2
                    }
                }
                @VerificationCase
                class Host : Base {
                    tran {
                        counter := 2
                    }
                }
            """,
            """
                init {
                }

                tran {
                    root.counter := 1
                }

                prop {
                    EF root.counter == 2
                }
            """,
        )
    }

    @Test
    fun `a call on an instance of a subclass runs the redefinition`() {
        assertInlinedModel(
            "Host",
            hierarchy + """
                @VerificationCase
                class Host {
                    contains child: Child[1]
                    redefine tran {
                        inline child.act()
                    }
                    redefine prop {
                        return EF child.count == 10
                    }
                }
            """,
            """
                init {
                }

                tran {
                    root.child.count := 10
                }

                prop {
                    EF root.child.count == 10
                }
            """,
        )
    }

    @Test
    fun `a call runs the redefinition of the most derived class only`() {
        assertInlinedModel(
            "Host",
            hierarchy + """
                @VerificationCase
                class Host {
                    contains leaf: Leaf[1]
                    redefine tran {
                        inline leaf.act()
                    }
                    redefine prop {
                        return AG leaf.middleCount == 0
                    }
                }
            """,
            """
                init {
                }

                tran {
                    root.leaf.leafCount := 2
                }

                prop {
                    AG root.leaf.middleCount == 0
                }
            """,
        )
    }

    @Test
    fun `a call through a base-typed feature runs the redefinition of the instance it holds`() {
        assertInlinedModel(
            "Host",
            hierarchy + """
                @VerificationCase
                class Host {
                    contains bases: Base[0..*]
                    contains child: Child[1] subsets bases
                    redefine tran {
                        inline bases.act()
                    }
                    redefine prop {
                        return AG (child.count == 0 || child.count == 10)
                    }
                }
            """,
            """
                init {
                }

                tran {
                    root.child.count := 10
                }

                prop {
                    AG root.child.count == 0 || root.child.count == 10
                }
            """,
        )
    }

    @Test
    fun `a call to an abstract member runs the redefinition of the concrete class`() {
        assertInlinedModel(
            "Host",
            """
                package inlining::tests
                abstract class Sensor {
                    var count: int := 0
                    abstract prop threshold(): bool
                    abstract tran tick()
                }
                class ThresholdSensor : Sensor {
                    redefine prop threshold(): bool {
                        return count >= 3
                    }
                    redefine tran tick() {
                        assume(count < 3)
                        count := count + 1
                    }
                }
                @VerificationCase
                class Host {
                    contains thresholdSensor: ThresholdSensor[1]
                    redefine tran {
                        inline thresholdSensor.tick()
                    }
                    redefine prop {
                        return EF thresholdSensor.threshold()
                    }
                }
            """,
            """
                init {
                }

                tran {
                    assume(root.thresholdSensor.count < 3)
                    root.thresholdSensor.count := root.thresholdSensor.count + 1
                }

                prop {
                    EF root.thresholdSensor.count >= 3
                }
            """,
        )
    }

    @Test
    fun `a redefined containment stays in its superset whether it restates subsets or not`() {
        assertInlinedModel(
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
                class RestatingHolder : Holder {
                    redefine contains first: SpecialItem[1] subsets items
                }
                class OmittingHolder : Holder {
                    redefine contains first: SpecialItem[1]
                }
                @VerificationCase
                class Host {
                    contains restating: RestatingHolder[1]
                    contains omitting: OmittingHolder[1]
                    redefine tran {
                        inline for (item in restating.items) {
                            inline item.act()
                        }
                        inline for (item in omitting.items) {
                            inline item.act()
                        }
                    }
                    redefine prop {
                        return EF omitting.first.special == 1
                    }
                }
            """,
            """
                init {
                }

                tran {
                    root.restating.first.special := root.restating.first.special + 1
                    root.omitting.first.special := root.omitting.first.special + 1
                }

                prop {
                    EF root.omitting.first.special == 1
                }
            """,
        )
    }
}
