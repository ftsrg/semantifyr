/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.clock

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.flattening.FlatteningTestBase
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler
import hu.bme.mit.semantifyr.oxsts.lang.semantics.clock.ClockTestAnalysis
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.xtext.EcoreUtil2
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class ClockTestClosednessTest : FlatteningTestBase() {

    @Inject
    private lateinit var clockTestAnalysis: ClockTestAnalysis

    @Inject
    private lateinit var parseHelper: OxstsPackageParseHelper

    @Inject
    private lateinit var builtinAnnotationHandler: BuiltinAnnotationHandler

    private fun unclosedTests(className: String): List<ComparisonOperator> {
        return clockTestAnalysis.unclosedTests(flatten(className, MODEL).inlinedOxsts.mainTransition)
    }

    @TestFactory
    fun `optimization keeps whether a clock case is closed`(): List<DynamicTest> {
        val cases = EcoreUtil2.eAllOfType(parseHelper.parse(CLOCK_CASES).oxstsPackage, ClassDeclaration::class.java).filter {
            builtinAnnotationHandler.isVerificationCase(it)
        }
        return cases.map {
            DynamicTest.dynamicTest(it.name) {
                val unoptimized = unclosedTestTexts(it.name, OptimizationConfig.NONE)
                val optimized = unclosedTestTexts(it.name, OptimizationConfig.ALL)
                assertThat(optimized).isEqualTo(unoptimized)
            }
        }
    }

    private fun unclosedTestTexts(className: String, optimization: OptimizationConfig): Set<String> {
        val mainTransition = flatten(className, CLOCK_CASES, optimization).inlinedOxsts.mainTransition
        return clockTestAnalysis.unclosedTests(mainTransition).map {
            serialize(it).trim()
        }.toSet()
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
        // the closed and the unclosed shape of each kind of clock test an optimization could rewrite
        private val CLOCK_CASES = """
            package clock::tests::optimization

            class Timer {
                var elapsed: clock := 0

                tran restart() {
                    elapsed := 0
                }
            }

            @VerificationCase
            class ClosedBounds {
                var elapsed: clock := 0
                var late: bool := false

                redefine tran {
                    choice {
                        assume(elapsed <= 5)
                    } or {
                        assume(elapsed >= 6)
                        late := true
                        elapsed := 0
                    }
                }

                redefine prop {
                    return EF late
                }
            }

            @VerificationCase
            class StrictBounds {
                var elapsed: clock := 0
                var hit: bool := false

                redefine tran {
                    assume(elapsed <= 1)
                    choice {
                        assume(elapsed > 0 && elapsed < 1)
                        hit := true
                    } or {
                    }
                }

                redefine prop {
                    return EF hit
                }
            }

            @VerificationCase
            class NegatedBounds {
                var elapsed: clock := 0
                var hit: bool := false

                redefine tran {
                    assume(elapsed <= 6)
                    choice {
                        assume(!(elapsed <= 5))
                        hit := true
                    } or {
                        assume(!(elapsed < 5))
                        hit := true
                    } or {
                    }
                }

                redefine prop {
                    return EF hit
                }
            }

            @VerificationCase
            class Equalities {
                var elapsed: clock := 0
                var hit: bool := false

                redefine tran {
                    assume(elapsed <= 3)
                    choice {
                        assume(elapsed == 3)
                        hit := true
                    } or {
                        assume(elapsed != 0)
                        hit := true
                    } or {
                    }
                }

                redefine prop {
                    return EF hit
                }
            }

            @VerificationCase
            class GuardedTest {
                var elapsed: clock := 0
                var hit: bool := false

                redefine tran {
                    assume(elapsed <= 2)
                    if (elapsed >= 2) {
                        hit := true
                    }
                }

                redefine prop {
                    return EF hit
                }
            }

            @VerificationCase
            class StoredTest {
                var elapsed: clock := 0
                var late: bool := false

                redefine tran {
                    assume(elapsed <= 2)
                    late := elapsed >= 2
                }

                redefine prop {
                    return EF late
                }
            }

            @VerificationCase
            class ConditionalValue {
                var elapsed: clock := 0
                var count: int := 0

                redefine tran {
                    assume(elapsed <= 2)
                    count := if (elapsed >= 2) then 1 else 0
                }

                redefine prop {
                    return EF count == 1
                }
            }

            @VerificationCase
            class StrictTestAfterReset {
                var elapsed: clock := 0
                var late: bool := false

                redefine tran {
                    assume(elapsed <= 2)
                    elapsed := 0
                    choice {
                        assume(elapsed > 0)
                        late := true
                    } or {
                    }
                }

                redefine prop {
                    return AG !late
                }
            }

            @VerificationCase
            class ClockThroughReference {
                contains first: Timer
                contains second: Timer
                var current: Timer := first
                var hit: bool := false

                redefine tran {
                    assume(second.elapsed <= 2)
                    inline current.restart()
                    current := second
                    choice {
                        assume(current.elapsed >= 1)
                        hit := true
                    } or {
                    }
                }

                redefine prop {
                    return EF hit
                }
            }
        """.trimIndent()

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

                redefine prop {
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

                redefine prop {
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

                redefine prop {
                    return AG true
                }
            }
        """.trimIndent()
    }

}
