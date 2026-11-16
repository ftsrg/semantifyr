/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.clock

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.semantics.clock.ClockTestAnalysis
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts

class ClockTestClosedness(
    val unclosedTests: List<ComparisonOperator>,
) {

    val isClosed: Boolean
        get() = unclosedTests.isEmpty()

}

class ClockTestClosednessAnalysis @Inject constructor(
    private val clockTestAnalysis: ClockTestAnalysis,
) {

    fun analyze(inlinedOxsts: InlinedOxsts): ClockTestClosedness {
        val unclosedTests = inlinedOxsts.mainTransition.branches.flatMap {
            clockTestAnalysis.unclosedTests(it)
        }
        return ClockTestClosedness(unclosedTests)
    }

}
