/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Worklist
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticBinaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

fun Expression.asIntegerValueOrNull(): Int? {
    if (this is LiteralInteger) {
        return value
    }
    return null
}

class ArithmeticIdentityPattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is ArithmeticBinaryOperator) {
            return false
        }

        val leftValue = element.left.asIntegerValueOrNull()
        val rightValue = element.right.asIntegerValueOrNull()
        val parent = element.eContainer() ?: return false

        val replacement = when (element.op) {
            ArithmeticOp.ADD -> when {
                leftValue == 0 -> element.right
                rightValue == 0 -> element.left
                else -> return false
            }
            ArithmeticOp.SUB -> when {
                rightValue == 0 -> element.left
                leftValue == 0 -> OxstsFactory.createArithmeticUnaryOperator().also {
                    it.op = UnaryOp.MINUS
                    it.body = element.right
                }
                else -> return false
            }
            ArithmeticOp.MUL -> when {
                leftValue == 0 || rightValue == 0 -> OxstsFactory.createLiteralInteger(0)
                leftValue == 1 -> element.right
                rightValue == 1 -> element.left
                else -> return false
            }
            ArithmeticOp.DIV -> when {
                rightValue == 1 -> element.left
                else -> return false
            }
            else -> return false
        }

        EcoreUtil2.replace(element, replacement)
        worklist.add(replacement)
        worklist.add(parent)
        return true
    }
}

class DoubleUnaryMinusPattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is ArithmeticUnaryOperator || element.op != UnaryOp.MINUS) {
            return false
        }
        val inner = element.body as? ArithmeticUnaryOperator ?: return false
        if (inner.op != UnaryOp.MINUS) {
            return false
        }
        val parent = element.eContainer() ?: return false

        EcoreUtil2.replace(element, inner.body)
        worklist.add(parent)
        return true
    }
}
