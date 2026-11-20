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
import kotlin.io.path.Path
import kotlin.io.path.readText

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
        val source = CLOCK_CORPUS.readText()
        val cases = EcoreUtil2.eAllOfType(parseHelper.parse(source).oxstsPackage, ClassDeclaration::class.java).filter {
            builtinAnnotationHandler.isVerificationCase(it)
        }
        return cases.map {
            DynamicTest.dynamicTest(it.name) {
                val unoptimized = unclosedTestTexts(it.name, source, OptimizationConfig.NONE)
                val optimized = unclosedTestTexts(it.name, source, OptimizationConfig.ALL)
                assertThat(optimized).isEqualTo(unoptimized)
            }
        }
    }

    private fun unclosedTestTexts(className: String, source: String, optimization: OptimizationConfig): Set<String> {
        val mainTransition = flatten(className, source, optimization).inlinedOxsts.mainTransition
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
        private val CLOCK_CORPUS = Path("../../oxsts-test-models/semantic/clocks.semantifyr")

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
