/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.clock

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.lang.semantics.clock.ClockTestAnalysis
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.eclipse.xtext.EcoreUtil2

// a witness states its own time and clock values already scaled, so only the bounds the inlining brought in count the scale
class WitnessTimeScaler @Inject constructor(
    private val clockTestAnalysis: ClockTestAnalysis,
) {

    fun ownComparisons(inlinedOxsts: InlinedOxsts): Set<ComparisonOperator> {
        if (inlinedOxsts.timeScale == 1) {
            return emptySet()
        }
        return (inlinedOxsts.initTransition.eAllOfType<ComparisonOperator>() + inlinedOxsts.mainTransition.eAllOfType<ComparisonOperator>()).toSet()
    }

    fun scale(inlinedOxsts: InlinedOxsts, ownComparisons: Set<ComparisonOperator>) {
        val scale = inlinedOxsts.timeScale
        if (scale == 1) {
            return
        }
        val clockTests = inlinedOxsts.eAllOfType<ComparisonOperator>().filter {
            it !in ownComparisons && clockTestAnalysis.isClockTest(it)
        }.toList()
        for (clockTest in clockTests) {
            val bound = if (clockTestAnalysis.isClockValued(clockTest.left)) {
                clockTest.right
            } else {
                clockTest.left
            }
            val product = OxstsFactory.createArithmeticBinaryOperator().also {
                it.op = ArithmeticOp.MUL
                it.right = OxstsFactory.createLiteralInteger(scale)
            }
            EcoreUtil2.replace(bound, product)
            product.left = bound
        }
    }

}
