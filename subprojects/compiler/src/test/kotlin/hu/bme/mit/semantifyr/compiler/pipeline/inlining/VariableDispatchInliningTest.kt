/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.TestModels.testModel
import org.junit.jupiter.api.Test

class VariableDispatchInliningTest : InliningTestBase() {

    private val worker = """
        package inlining::tests
        class Worker {
            var done: bool := false
            tran step() {
                done := true
            }
        }
    """

    @Test
    fun `a call through a feature-typed variable chooses among the instances of the feature`() {
        assertInlinedModel(
            "Host",
            worker + testModel(javaClass, "feature_typed_variable.semantifyr"),
            """
                init {
                    root.current := root.first
                }

                tran {
                    choice {
                        assume(root.current == root.first)
                        root.first.done := true
                    } or {
                        assume(root.current == root.second)
                        root.second.done := true
                    } or {
                        assume(root.current == nothing)
                    }
                }

                prop {
                    EF root.first.done
                }
            """,
        )
    }

    @Test
    fun `a call through a class-typed variable chooses among the instances of the class`() {
        assertInlinedModel(
            "Host",
            worker + testModel(javaClass, "class_typed_variable.semantifyr"),
            """
                init {
                    choice {
                        root.current := root.first
                    } or {
                        root.current := root.second
                    }
                }

                tran {
                    choice {
                        assume(root.current == root.first)
                        root.first.done := true
                    } or {
                        assume(root.current == root.second)
                        root.second.done := true
                    } or {
                        assume(root.current == nothing)
                    }
                }

                prop {
                    AG root.current == nothing || root.current == root.first || root.current == root.second
                }
            """,
        )
    }

    @Test
    fun `a variable passed as an argument dispatches inside the callee`() {
        assertInlinedModel(
            "Host",
            worker + testModel(javaClass, "variable_argument.semantifyr"),
            $$$$"""
                init {
                    root.current := root.first
                }

                tran {
                    var 'worker$$$local0': inlining::tests::Worker[0..1] := root.current
                    choice {
                        assume('worker$$$local0' == root.first)
                        root.first.done := true
                    } or {
                        assume('worker$$$local0' == root.second)
                        root.second.done := true
                    } or {
                        assume('worker$$$local0' == nothing)
                    }
                }

                prop {
                    EF root.first.done
                }
            """,
        )
    }
}
