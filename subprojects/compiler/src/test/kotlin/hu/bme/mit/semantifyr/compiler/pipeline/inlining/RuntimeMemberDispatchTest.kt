/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RuntimeMemberDispatchTest : InliningTestBase() {

    private val worker = """
        package inlining::tests
        class Part {
            var done: bool := false
            tran step() {
                done := true
            }
            prop isDone(): bool {
                return done
            }
        }
        class Worker {
            var done: bool := false
            var slot: int := 0
            contains part: Part[1]
        }
        class Other {
            var done: bool := false
        }
    """

    @Test
    fun `a read through a feature-typed variable in a transition dispatches over the instances of the feature`() {
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
                    var seen: bool := false
                    redefine tran {
                        seen := current.done
                    }
                    prop {
                        return EF seen
                    }
                }
            """,
            """
                init {
                }

                tran {
                    root.seen := if root.current == root.first then root.first.done else root.second.done
                }

                prop {
                    EF root.seen
                }
            """,
        )
    }

    @Test
    fun `a read through a class-typed variable in a property dispatches over the instances of the class`() {
        assertInlinedModel(
            "Host",
            worker + """
                @VerificationCase
                class Host {
                    contains first: Worker[1]
                    contains second: Worker[1]
                    contains other: Other[1]
                    var current: Worker[0..1] := nothing
                    prop {
                        return EF current.done
                    }
                }
            """,
            """
                init {
                }

                tran {
                }

                prop {
                    EF if root.current == root.first then root.first.done else root.second.done
                }
            """,
        )
    }

    @Test
    fun `a read through a class-typed parameter of a property dispatches over the instances of the class`() {
        assertInlinedModel(
            "Host",
            worker + """
                @VerificationCase
                class Host {
                    contains first: Worker[1]
                    contains second: Worker[1]
                    var current: Worker[0..1] := nothing
                    prop isDoneOf(worker: Worker[0..1]): bool {
                        return worker.done
                    }
                    prop {
                        return EF isDoneOf(current)
                    }
                }
            """,
            """
                init {
                }

                tran {
                }

                prop {
                    EF if root.current == root.first then root.first.done else root.second.done
                }
            """,
        )
    }

    @Test
    fun `a read through a class-typed parameter of a transition dispatches over the instances of the class`() {
        assertInlinedModel(
            "Host",
            worker + """
                @VerificationCase
                class Host {
                    contains first: Worker[1]
                    contains second: Worker[1]
                    var current: Worker[0..1] := nothing
                    var seen: bool := false
                    tran observe(worker: Worker[0..1]) {
                        seen := worker.done
                    }
                    redefine tran {
                        inline observe(current)
                    }
                    prop {
                        return EF seen
                    }
                }
            """,
            $$$$"""
                init {
                }

                tran {
                    var 'worker$$$local0': inlining::tests::Worker[0..1] := root.current
                    root.seen := if 'worker$$$local0' == root.first then root.first.done else root.second.done
                }

                prop {
                    EF root.seen
                }
            """,
        )
    }

    @Test
    fun `a chain dispatches on the runtime holder and resolves the rest on each candidate`() {
        assertInlinedModel(
            "Host",
            worker + """
                @VerificationCase
                class Host {
                    contains first: Worker[1]
                    contains second: Worker[1]
                    var current: Worker[0..1] := nothing
                    var seen: bool := false
                    redefine tran {
                        seen := current.part.done
                    }
                    prop {
                        return EF current.part.done
                    }
                }
            """,
            """
                init {
                }

                tran {
                    root.seen := if root.current == root.first then root.first.part.done else root.second.part.done
                }

                prop {
                    EF if root.current == root.first then root.first.part.done else root.second.part.done
                }
            """,
        )
    }

    @Test
    fun `a call through a chain dispatches on the runtime holder and calls on each candidate`() {
        assertInlinedModel(
            "Host",
            worker + """
                @VerificationCase
                class Host {
                    contains first: Worker[1]
                    contains second: Worker[1]
                    var current: Worker[0..1] := nothing
                    redefine tran {
                        inline current.part.step()
                    }
                    prop {
                        return EF current.part.isDone()
                    }
                }
            """,
            """
                init {
                }

                tran {
                    choice {
                        assume(root.current == root.first)
                        root.first.part.done := true
                    } or {
                        assume(root.current == root.second)
                        root.second.part.done := true
                    }
                }

                prop {
                    EF if root.current == root.first then root.first.part.done else root.second.part.done
                }
            """,
        )
    }

    @Test
    fun `a read through a variable in the index of a write target dispatches`() {
        assertInlinedModel(
            "Host",
            worker + """
                @VerificationCase
                class Host {
                    contains first: Worker[1]
                    contains second: Worker[1]
                    var current: Worker[0..1] := nothing
                    var flags: bool[2] := [false, false]
                    redefine tran {
                        flags[current.slot] := true
                    }
                    prop {
                        return EF flags[1]
                    }
                }
            """,
            """
                init {
                }

                tran {
                    root.flags[if root.current == root.first then root.first.slot else root.second.slot] := true
                }

                prop {
                    EF root.flags[1]
                }
            """,
        )
    }

    @Test
    fun `a read through a runtime for loop variable dispatches over the instances of the range`() {
        assertInlinedModel(
            "Host",
            worker + """
                @VerificationCase
                class Host {
                    contains workers: Worker[0..*]
                    contains first: Worker[1] subsets workers
                    contains second: Worker[1] subsets workers
                    var count: int := 0
                    redefine tran {
                        for (worker in workers) {
                            if (worker.done) {
                                count := count + 1
                            }
                        }
                    }
                    prop {
                        return EF count == 2
                    }
                }
            """,
            """
                init {
                }

                tran {
                    for (worker in [root.first, root.second]) {
                        if (if worker == root.first then root.first.done else root.second.done) {
                            root.count := root.count + 1
                        }
                    }
                }

                prop {
                    EF root.count == 2
                }
            """,
        )
    }

    @Test
    fun `a read through an array slot dispatches on the slot`() {
        assertInlinedModel(
            "Host",
            worker + """
                @VerificationCase
                class Host {
                    contains first: Worker[1]
                    contains second: Worker[1]
                    var queue: Worker[2] := [first, second]
                    prop {
                        return EF queue[1].done
                    }
                }
            """,
            """
                init {
                }

                tran {
                }

                prop {
                    EF if root.queue[1] == root.first then root.first.done else root.second.done
                }
            """,
        )
    }

    @Test
    fun `a write through a variable fails at its source`() {
        assertThatThrownBy {
            assertInlinedModel(
                "Host",
                worker + """
                    @VerificationCase
                    class Host {
                        contains first: Worker[1]
                        var current: Worker[0..1] := nothing
                        redefine tran {
                            current.done := true
                        }
                        prop {
                            return EF first.done
                        }
                    }
                """,
                "",
            )
        }.isInstanceOf(IllegalStateException::class.java).hasMessageContaining("Writing through a variable is not supported.")
    }
}
