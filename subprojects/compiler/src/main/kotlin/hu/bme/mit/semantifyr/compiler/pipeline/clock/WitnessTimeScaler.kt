/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.clock

import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.lang.semantics.clock.ClockTest
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.eclipse.xtext.EcoreUtil2

class WitnessTimeScaler {

    fun ownComparisons(inlinedOxsts: InlinedOxsts): Set<ComparisonOperator> {
        if (inlinedOxsts.timeScale == 1) {
            return emptySet()
        }
        return (inlinedOxsts.initTransition.eAllOfType<ComparisonOperator>() + inlinedOxsts.mainTransition.eAllOfType<ComparisonOperator>()).toSet()
    }

    fun scale(
        inlinedOxsts: InlinedOxsts,
        clockTests: List<ClockTest>,
        ownComparisons: Set<ComparisonOperator>,
    ) {
        val scale = inlinedOxsts.timeScale
        if (scale == 1) {
            return
        }
        for (clockTest in clockTests) {
            if (clockTest.comparison in ownComparisons) {
                continue
            }
            val product = OxstsFactory.createArithmeticBinaryOperator().also {
                it.op = ArithmeticOp.MUL
                it.right = OxstsFactory.createLiteralInteger(scale)
            }
            EcoreUtil2.replace(clockTest.bound, product)
            product.left = clockTest.bound
        }
    }

}
