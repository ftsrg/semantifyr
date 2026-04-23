/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Worklist
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticBinaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.xtext.EcoreUtil2

internal fun Expression.structurallyEquals(other: Expression): Boolean {
    return EcoreUtil.equals(this, other)
}

internal fun Expression.isPure(): Boolean {
    return !OxstsUtils.isWriteExpression(this)
}

class SelfArithmeticPattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is ArithmeticBinaryOperator) {
            return false
        }
        if (element.op != ArithmeticOp.SUB) {
            return false
        }
        if (!element.left.isPure() || !element.right.isPure()) {
            return false
        }
        if (!element.left.structurallyEquals(element.right)) {
            return false
        }

        val parent = element.eContainer() ?: return false
        EcoreUtil2.replace(element, OxstsFactory.createLiteralInteger(0))
        worklist.add(parent)
        return true
    }
}

class SelfComparisonPattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is ComparisonOperator) {
            return false
        }
        if (!element.left.isPure() || !element.right.isPure()) {
            return false
        }
        if (!element.left.structurallyEquals(element.right)) {
            return false
        }

        val result = when (element.op) {
            ComparisonOp.EQ, ComparisonOp.LESS_EQ, ComparisonOp.GREATER_EQ -> true
            ComparisonOp.NOT_EQ, ComparisonOp.LESS, ComparisonOp.GREATER -> false
        }
        val parent = element.eContainer() ?: return false
        EcoreUtil2.replace(element, OxstsFactory.createLiteralBoolean(result))
        worklist.add(parent)
        return true
    }
}
