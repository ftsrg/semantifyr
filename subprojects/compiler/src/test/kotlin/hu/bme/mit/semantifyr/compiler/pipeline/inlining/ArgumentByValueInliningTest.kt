/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import org.junit.jupiter.api.Test

class ArgumentByValueInliningTest : InliningTestBase() {

    @Test
    fun `swap reads both arguments before the body writes either`() {
        assertInlinedModel(
            "Host",
            """
                package inlining::tests
                @VerificationCase
                class Host {
                    var x: int := 1
                    var y: int := 2
                    tran swap(first: int, second: int) {
                        x := second
                        y := first
                    }
                    redefine tran {
                        inline swap(x, y)
                    }
                    prop {
                        return EF x == 2 && y == 1
                    }
                }
            """,
            $$$$"""
                init {
                }

                tran {
                    var 'first$$$local0' : int := root.x
                    var 'second$$$local1' : int := root.y
                    root.x := 'second$$$local1'
                    root.y := 'first$$$local0'
                }

                prop {
                    EF root.x == 2 && root.y == 1
                }
            """,
        )
    }

    @Test
    fun `a parameter read after the body writes its argument reads the value at the call`() {
        assertInlinedModel(
            "Host",
            """
                package inlining::tests
                @VerificationCase
                class Host {
                    var counter: int := 0
                    var previous: int := 0
                    tran increment(old: int) {
                        counter := counter + 1
                        previous := old
                    }
                    redefine tran {
                        inline increment(counter)
                    }
                    prop {
                        return AG previous < counter
                    }
                }
            """,
            $$$$"""
                init {
                }

                tran {
                    var 'old$$$local0' : int := root.counter
                    root.counter := root.counter + 1
                    root.previous := 'old$$$local0'
                }

                prop {
                    AG root.previous < root.counter
                }
            """,
        )
    }

    @Test
    fun `a constant argument is substituted`() {
        assertInlinedModel(
            "Host",
            """
                package inlining::tests
                @VerificationCase
                class Host {
                    var counter: int := 0
                    tran add(amount: int) {
                        counter := counter + amount
                    }
                    redefine tran {
                        inline add(2)
                    }
                    prop {
                        return EF counter == 2
                    }
                }
            """,
            """
                init {
                }

                tran {
                    root.counter := root.counter + 2
                }

                prop {
                    EF root.counter == 2
                }
            """,
        )
    }

}
