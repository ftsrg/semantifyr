/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.flattening

import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.assumeBeforeEvaluation
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.pipeline.utils.isDivisionByPossibleZero
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticBinaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts

fun addDivisionAssumptions(inlinedOxsts: InlinedOxsts): Boolean {
    val divisions = inlinedOxsts.eAllOfType<ArithmeticBinaryOperator>().filter {
        it.isDivisionByPossibleZero()
    }.toList()
    // a division inside a divisor is assumed first, so no assumption divides by zero
    return divisions.asReversed().map { division ->
        division.assumeBeforeEvaluation(
            OxstsFactory.createComparisonOperator().also {
                it.op = ComparisonOp.NOT_EQ
                it.left = division.right.copy()
                it.right = OxstsFactory.createLiteralInteger(0)
            },
        )
    }.any {
        it
    }
}
