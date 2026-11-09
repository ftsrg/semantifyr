/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

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
            worker + """
                @VerificationCase
                class Host {
                    contains workers: Worker[0..*]
                    contains first: Worker[1] subsets workers
                    contains second: Worker[1] subsets workers
                    contains spare: Worker[1]
                    var current: workers[0..1] := nothing
                    redefine init {
                        current := first
                    }
                    redefine tran {
                        inline current.step()
                    }
                    prop {
                        return EF first.done
                    }
                }
            """,
            """
                init {
                    root.current := root.first
                }

                tran {
                    choice {
                        assume (root.current == root.first)
                        root.first.done := true
                    } or {
                        assume (root.current == root.second)
                        root.second.done := true
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
            worker + """
                class Other {
                    var done: bool := false
                    tran step() {
                        done := true
                    }
                }
                @VerificationCase
                class Host {
                    contains first: Worker[1]
                    contains second: Worker[1]
                    contains other: Other[1]
                    var current: Worker[0..1] := nothing
                    redefine init {
                        choice {
                            current := first
                        } or {
                            current := second
                        }
                    }
                    redefine tran {
                        inline current.step()
                    }
                    prop {
                        return AG (current == nothing || current == first || current == second)
                    }
                }
            """,
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
                        assume (root.current == root.first)
                        root.first.done := true
                    } or {
                        assume (root.current == root.second)
                        root.second.done := true
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
            worker + """
                @VerificationCase
                class Host {
                    contains workers: Worker[0..*]
                    contains first: Worker[1] subsets workers
                    contains second: Worker[1] subsets workers
                    var current: workers[0..1] := nothing
                    tran run(worker: Worker[0..1]) {
                        inline worker.step()
                    }
                    redefine init {
                        current := first
                    }
                    redefine tran {
                        inline run(current)
                    }
                    prop {
                        return EF first.done
                    }
                }
            """,
            $$$$"""
                init {
                    root.current := root.first
                }

                tran {
                    var 'worker$$$local0' : inlining::tests::Worker[0..1] := root.current
                    choice {
                        assume ('worker$$$local0' == root.first)
                        root.first.done := true
                    } or {
                        assume ('worker$$$local0' == root.second)
                        root.second.done := true
                    }
                }

                prop {
                    EF root.first.done
                }
            """,
        )
    }
}
