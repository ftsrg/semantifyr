/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.clock

import hu.bme.mit.semantifyr.compiler.pipeline.flattening.FlatteningTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ClockBoundCheckerTest : FlatteningTestBase() {

    @Test
    fun `a bound that a run-time argument binds is refused on the inlined model`() {
        val exception = assertThrows<IllegalStateException> {
            flatten("RunTimeBound", MODEL)
        }

        assertThat(exception.message).contains("known only at run time")
    }

    @Test
    fun `a bound read from an array slot is refused, since a slot holds a run-time value`() {
        val exception = assertThrows<IllegalStateException> {
            flatten("SlotBound", MODEL)
        }

        assertThat(exception.message).contains("known only at run time")
    }

    @Test
    fun `a bound that a constant argument or a feature binds compiles`() {
        flatten("CompileTimeBound", MODEL)
    }

    @Test
    fun `a clock that the verified property reads through a property it calls is refused`() {
        val exception = assertThrows<IllegalStateException> {
            flatten("PropertyReadsClock", MODEL)
        }

        assertThat(exception.message).contains("The verified property reads a clock")
    }

    @Test
    fun `a property that reads a clock compiles in an assumption`() {
        flatten("AssumedProperty", MODEL)
    }

    companion object {
        private val MODEL = """
            package clock::tests::bound

            class Timer {
                refers deadline: int = 5
                var elapsed: clock := 0

                tran expire(limit: int) {
                    assume(elapsed >= limit)
                    assume(elapsed <= deadline)
                }

                prop isUp(): bool {
                    return elapsed >= deadline
                }
            }

            @VerificationCase
            class PropertyReadsClock {
                contains timer: Timer

                prop {
                    return AG !timer.isUp()
                }
            }

            @VerificationCase
            class AssumedProperty {
                contains timer: Timer
                var done: bool := false

                redefine tran {
                    assume(timer.isUp())
                    done := true
                }

                prop {
                    return AG true
                }
            }

            @VerificationCase
            class RunTimeBound {
                contains timer: Timer
                var limit: int := 3

                redefine tran {
                    inline timer.expire(limit)
                }

                prop {
                    return AG true
                }
            }

            @VerificationCase
            class SlotBound {
                contains timer: Timer
                var limits: int[2] := [3, 4]

                redefine tran {
                    inline timer.expire(limits[1])
                }

                prop {
                    return AG true
                }
            }

            @VerificationCase
            class CompileTimeBound {
                contains timer: Timer

                redefine tran {
                    inline timer.expire(3)
                }

                prop {
                    return AG true
                }
            }
        """.trimIndent()
    }

}
