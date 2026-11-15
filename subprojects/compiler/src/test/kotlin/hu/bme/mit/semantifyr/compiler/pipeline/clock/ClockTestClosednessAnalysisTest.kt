/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.clock

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.flattening.FlatteningTestBase
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.emf.ecore.util.EcoreUtil
import org.junit.jupiter.api.Test

class ClockTestClosednessAnalysisTest : FlatteningTestBase() {

    @Inject
    private lateinit var clockTestClosednessAnalysis: ClockTestClosednessAnalysis

    @Test
    fun `a model whose clock tests stand in assumptions as closed comparisons is closed`() {
        val compilation = flatten("Deadline", MODEL)

        assertThat(clockTestClosednessAnalysis.analyze(compilation.inlinedOxsts).isClosed).isTrue()
    }

    @Test
    fun `a reset in a called transition exempts the test after the call`() {
        val compilation = flatten("RestartThenCheck", MODEL)

        assertThat(clockTestClosednessAnalysis.analyze(compilation.inlinedOxsts).isClosed).isTrue()
    }

    @Test
    fun `a test that init calls is exempt and one that the main transition calls is not`() {
        val compilation = flatten("CheckInInitAndStep", MODEL)

        val closedness = clockTestClosednessAnalysis.analyze(compilation.inlinedOxsts)

        assertThat(closedness.unclosedTests).singleElement().satisfies({
            assertThat(EcoreUtil.isAncestor(compilation.inlinedOxsts.mainTransition, it)).isTrue()
        })
    }

    @Test
    fun `a closed test of a clock read through a variable holding an instance is closed`() {
        val compilation = flatten("DispatchedDeadline", MODEL)

        assertThat(clockTestClosednessAnalysis.analyze(compilation.inlinedOxsts).isClosed).isTrue()
    }

    @Test
    fun `a strict test of a clock read through a variable holding an instance is not closed`() {
        val compilation = flatten("DispatchedStrictTest", MODEL)

        assertThat(clockTestClosednessAnalysis.analyze(compilation.inlinedOxsts).unclosedTests).hasSize(1)
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

            @VerificationCase
            class CheckInInitAndStep {
                contains timer: Timer

                redefine init {
                    inline timer.check()
                }

                redefine tran {
                    inline timer.check()
                }

                prop {
                    return AG true
                }
            }
        """.trimIndent()
    }

}
