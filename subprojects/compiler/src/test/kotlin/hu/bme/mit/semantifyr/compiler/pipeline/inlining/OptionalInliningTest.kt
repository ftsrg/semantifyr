/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.TestModels.testModel
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class OptionalInliningTest : InliningTestBase() {

    private val leaf = """
        package inlining::tests
        class Leaf {
            var count: int := 0
            tran bump() {
                count := count + 1
            }
        }
    """

    @Test
    fun `an optional navigation to a present instance inlines the call`() {
        assertInlinedModel(
            "Host",
            leaf + """
                @VerificationCase
                class Host {
                    contains leaves: Leaf[0..1]
                    contains leaf: Leaf[1] subsets leaves
                    redefine tran {
                        inline leaves?.bump()
                    }
                    redefine prop {
                        return EF leaf.count == 3
                    }
                }
            """,
            """
                init {
                }

                tran {
                    root.leaf.count := root.leaf.count + 1
                }

                prop {
                    EF root.leaf.count == 3
                }
            """,
        )
    }

    @Test
    fun `an optional navigation to an absent instance drops the call and keeps the rest of the step`() {
        assertInlinedModel(
            "Host",
            leaf + """
                @VerificationCase
                class Host {
                    contains leaf: Leaf[0..1]
                    var count: int := 0
                    redefine tran {
                        inline leaf?.bump()
                        assume(count < 1)
                        count := count + 1
                    }
                    redefine prop {
                        return EF count == 1
                    }
                }
            """,
            """
                init {
                }

                tran {
                    assume(root.count < 1)
                    root.count := root.count + 1
                }

                prop {
                    EF root.count == 1
                }
            """,
        )
    }

    @Test
    fun `a call through an absent instance without the optional navigation reports the empty holder`() {
        prepare(
            "Host",
            leaf + """
                @VerificationCase
                class Host {
                    contains leaf: Leaf[0..1]
                    redefine tran {
                        inline (leaf as Leaf).bump()
                    }
                    redefine prop {
                        return EF true
                    }
                }
            """,
        ) {
            assertThatThrownBy { inlineAll(it) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("The holder of the call to 'bump' evaluates to nothing")
        }
    }

    @Test
    fun `a call through a holder of several instances is rejected`() {
        prepare(
            "Host",
            leaf + """
                @VerificationCase
                class Host {
                    contains leaves: Leaf[0..1]
                    contains first: Leaf[1] subsets leaves
                    contains second: Leaf[1] subsets leaves
                    redefine tran {
                        inline leaves?.bump()
                    }
                    redefine prop {
                        return EF true
                    }
                }
            """,
        ) {
            assertThatThrownBy { inlineAll(it) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("The holder of the call to 'bump' evaluates to 2 instances")
        }
    }

    @Test
    fun `an inline if on an omitted optional argument takes its nothing branch`() {
        assertInlinedModel(
            "Host",
            testModel(javaClass, "inline_if_on_omitted_argument.semantifyr"),
            """
                init {
                }

                tran {
                    choice {
                        root.recorder.value := 5
                        root.recorder.marked := false
                    } or {
                        root.recorder.value := 7
                        root.recorder.marked := true
                    }
                }

                prop {
                    EF root.recorder.value == 5 && !root.recorder.marked
                }
            """,
        )
    }
}
