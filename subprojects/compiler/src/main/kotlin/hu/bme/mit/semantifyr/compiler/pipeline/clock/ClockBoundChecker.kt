/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.clock

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.pipeline.utils.sourceError
import hu.bme.mit.semantifyr.oxsts.lang.semantics.clock.ClockTestAnalysis
import hu.bme.mit.semantifyr.oxsts.lang.semantics.modality.ExpressionModalityEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.modality.Modality
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts

// a bound and what the verified property calls are known only once the calls are inlined
class ClockBoundChecker @Inject constructor(
    private val clockTestAnalysis: ClockTestAnalysis,
    private val expressionModalityEvaluatorProvider: ExpressionModalityEvaluatorProvider,
) {

    fun check(inlinedOxsts: InlinedOxsts) {
        val propertyClockRead = inlinedOxsts.property.eAllOfType<Expression>().firstOrNull {
            clockTestAnalysis.referencedClock(it) != null
        }
        if (propertyClockRead != null) {
            sourceError(propertyClockRead, "The verified property reads a clock through a property it calls; a timing requirement is modelled as a value that a timed branch sets.")
        }
        val clockTests = inlinedOxsts.eAllOfType<ComparisonOperator>().filter {
            clockTestAnalysis.isClockTest(it)
        }
        for (clockTest in clockTests) {
            val bound = if (clockTestAnalysis.isClockValued(clockTest.left)) {
                clockTest.right
            } else {
                clockTest.left
            }
            if (!expressionModalityEvaluatorProvider.evaluate(bound).isAtMost(Modality.COMPILE_TIME)) {
                sourceError(clockTest, "A clock is compared with a compile-time integer, but this bound is known only at run time.")
            }
        }
    }

}
