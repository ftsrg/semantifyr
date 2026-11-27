/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TemporalOperatorInliningTest : InliningTestBase() {

    @Test
    fun `a property without a temporal operator at its root fails compilation`() {
        prepare(
            "Host",
            """
                package inlining::tests
                @VerificationCase
                class Host {
                    var counter: int := 0
                    redefine prop {
                        return counter == 0
                    }
                }
            """,
        ) {
            assertThatThrownBy {
                inlineAll(it)
            }.isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Property body must have a temporal operator (`AG` or `EF`) at the top level.")
        }
    }

    @Test
    fun `a temporal operator below the root of the property fails compilation`() {
        prepare(
            "Host",
            """
                package inlining::tests
                @VerificationCase
                class Host {
                    var counter: int := 0
                    redefine prop {
                        return AG (EF counter == 0)
                    }
                }
            """,
        ) {
            assertThatThrownBy {
                inlineAll(it)
            }.isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("There are temporal operators that are not the root")
        }
    }

    @Test
    fun `a negated AG reaches the temporal check as EF of the negation with the optimizations off`() {
        assertInlinedModel(
            "Host",
            """
                package inlining::tests
                @VerificationCase
                class Host {
                    var flag: bool := false
                    redefine prop {
                        return !(AG flag)
                    }
                }
            """,
            """
                init {
                }

                tran {
                }

                prop {
                    EF !root.flag
                }
            """,
        )
    }

    @Test
    fun `a negated EF reaches the temporal check as AG of the negation with the optimizations off`() {
        assertInlinedModel(
            "Host",
            """
                package inlining::tests
                @VerificationCase
                class Host {
                    var flag: bool := false
                    redefine prop {
                        return !(EF flag)
                    }
                }
            """,
            """
                init {
                }

                tran {
                }

                prop {
                    AG !root.flag
                }
            """,
        )
    }
}
