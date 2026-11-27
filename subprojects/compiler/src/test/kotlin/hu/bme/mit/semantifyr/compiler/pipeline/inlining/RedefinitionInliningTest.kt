/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.TestModels.testModel
import org.junit.jupiter.api.Test

class RedefinitionInliningTest : InliningTestBase() {

    private val hierarchy = testModel(javaClass, "hierarchy.semantifyr")

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
            testModel(javaClass, "tran_without_redefine.semantifyr"),
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
            testModel(javaClass, "abstract_member_call.semantifyr"),
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
            testModel(javaClass, "redefined_containment.semantifyr"),
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
