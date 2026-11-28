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
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.TestModels.testModel
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
                val unoptimized = unclosedTestCount(it.name, OptimizationConfig.NONE)
                val optimized = unclosedTestCount(it.name, OptimizationConfig.ALL)
                assertThat(optimized).isEqualTo(unoptimized)
            }
        }
    }

    private fun unclosedTestCount(className: String, optimization: OptimizationConfig): Int {
        val mainTransition = flatten(className, CLOCK_CASES, optimization).inlinedOxsts.mainTransition
        return clockTestAnalysis.unclosedTests(mainTransition).size
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
        private val CLOCK_CASES = testModel(ClockTestClosednessTest::class.java, "clock_cases.semantifyr")

        private val MODEL = testModel(ClockTestClosednessTest::class.java, "model.semantifyr")
    }

}
