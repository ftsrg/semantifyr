/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Worklist
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NegationOperator
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

class NegatedComparisonPattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is NegationOperator) {
            return false
        }
        val comparison = element.body as? ComparisonOperator ?: return false
        val parent = element.eContainer() ?: return false

        val inverted = invert(comparison.op)
        val replacement = OxstsFactory.createComparisonOperator().also {
            it.op = inverted
            it.left = comparison.left
            it.right = comparison.right
        }
        EcoreUtil2.replace(element, replacement)
        worklist.add(replacement)
        worklist.add(parent)
        return true
    }

    private fun invert(op: ComparisonOp): ComparisonOp {
        return when (op) {
            ComparisonOp.EQ -> ComparisonOp.NOT_EQ
            ComparisonOp.NOT_EQ -> ComparisonOp.EQ
            ComparisonOp.LESS -> ComparisonOp.GREATER_EQ
            ComparisonOp.LESS_EQ -> ComparisonOp.GREATER
            ComparisonOp.GREATER -> ComparisonOp.LESS_EQ
            ComparisonOp.GREATER_EQ -> ComparisonOp.LESS
        }
    }
}

class DoubleNegationPattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is NegationOperator) {
            return false
        }
        val inner = element.body as? NegationOperator ?: return false
        val parent = element.eContainer() ?: return false
        EcoreUtil2.replace(element, inner.body)
        worklist.add(parent)
        return true
    }
}

class DeMorganPattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is NegationOperator) {
            return false
        }
        val boolean = element.body as? BooleanOperator ?: return false
        val parent = element.eContainer() ?: return false

        val flippedOp = when (boolean.op) {
            BooleanOp.AND -> BooleanOp.OR
            BooleanOp.OR -> BooleanOp.AND
            else -> return false
        }

        val replacement = OxstsFactory.createBooleanOperator(
            op = flippedOp,
            left = OxstsFactory.createNegationOperator(boolean.left),
            right = OxstsFactory.createNegationOperator(boolean.right),
        )
        EcoreUtil2.replace(element, replacement)
        worklist.add(replacement)
        worklist.add(parent)
        return true
    }
}
