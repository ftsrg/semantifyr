/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.clock

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.flattening.FlatteningTestBase
import hu.bme.mit.semantifyr.oxsts.lang.semantics.clock.ClockTestAnalysis
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClockTestClosednessTest : FlatteningTestBase() {

    @Inject
    private lateinit var clockTestAnalysis: ClockTestAnalysis

    private fun unclosedTests(className: String): List<ComparisonOperator> {
        return clockTestAnalysis.unclosedTests(flatten(className, MODEL).inlinedOxsts.mainTransition)
    }

    @Test
    fun `a model whose clock tests stand in assumptions as closed comparisons is closed`() {
        assertThat(unclosedTests("Deadline")).isEmpty()
    }

    @Test
    fun `a reset in a called transition exempts the test after the call`() {
        assertThat(unclosedTests("RestartThenCheck")).isEmpty()
    }

    @Test
    fun `a closed test of a clock read through a variable holding an instance is closed`() {
        assertThat(unclosedTests("DispatchedDeadline")).isEmpty()
    }

    @Test
    fun `a strict test of a clock read through a variable holding an instance is not closed`() {
        assertThat(unclosedTests("DispatchedStrictTest")).hasSize(1)
    }

    companion object {
        private val MODEL = """
            package clock::tests::closedness

            class Timer {
                var elapsed: clock := 0
                var early: bool := false

                tran restart() {
                    elapsed := 0
                }

                tran check() {
                    if (elapsed < 1) {
                        early := true
                    }
                }
            }

            @VerificationCase
            class Deadline {
                contains timer: Timer

                redefine tran {
                    assume(timer.elapsed <= 5)
                    choice {
                        assume(!(timer.elapsed < 2))
                        inline timer.restart()
                    } or {
                        assume(timer.elapsed == 1)
                    }
                }

                prop {
                    return AG true
                }
            }

            @VerificationCase
            class DispatchedDeadline {
                contains first: Timer
                contains second: Timer
                var current: Timer := first

                redefine tran {
                    assume(current.elapsed <= 5)
                    choice {
                        current := first
                    } or {
                        current := second
                    }
                }

                prop {
                    return AG true
                }
            }

            @VerificationCase
            class DispatchedStrictTest {
                contains first: Timer
                contains second: Timer
                var current: Timer := first

                redefine tran {
                    assume(current.elapsed < 5)
                }

                prop {
                    return AG true
                }
            }

            @VerificationCase
            class RestartThenCheck {
                contains timer: Timer

                redefine tran {
                    inline timer.restart()
                    inline timer.check()
                }

                prop {
                    return AG true
                }
            }
        """.trimIndent()
    }

}
