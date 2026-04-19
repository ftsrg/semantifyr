/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Worklist
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOperator
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

class IdempotentBooleanPattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is BooleanOperator) return false
        if (!element.left.isPure() || !element.right.isPure()) return false
        if (!element.left.structurallyEquals(element.right)) return false

        val parent = element.eContainer() ?: return false
        EcoreUtil2.replace(element, element.left)
        worklist.add(parent)
        return true
    }
}

class RedundantAndPattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is BooleanOperator || element.op != BooleanOp.AND) return false
        val replacement = when {
            OxstsUtils.isConstantLiteralTrue(element.left) -> element.right
            OxstsUtils.isConstantLiteralTrue(element.right) -> element.left
            else -> return false
        }
        val parent = element.eContainer() ?: return false
        EcoreUtil2.replace(element, replacement)
        worklist.add(parent)
        return true
    }
}

class RedundantOrPattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is BooleanOperator || element.op != BooleanOp.OR) return false
        val replacement = when {
            OxstsUtils.isConstantLiteralFalse(element.left) -> element.right
            OxstsUtils.isConstantLiteralFalse(element.right) -> element.left
            else -> return false
        }
        val parent = element.eContainer() ?: return false
        EcoreUtil2.replace(element, replacement)
        worklist.add(parent)
        return true
    }
}

class ConstantFalseAndPattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is BooleanOperator || element.op != BooleanOp.AND) return false
        val replacement = when {
            OxstsUtils.isConstantLiteralFalse(element.left) -> element.left
            OxstsUtils.isConstantLiteralFalse(element.right) -> element.right
            else -> return false
        }
        val parent = element.eContainer() ?: return false
        EcoreUtil2.replace(element, replacement)
        worklist.add(parent)
        return true
    }
}

class ConstantTrueOrPattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is BooleanOperator || element.op != BooleanOp.OR) return false
        val replacement = when {
            OxstsUtils.isConstantLiteralTrue(element.left) -> element.left
            OxstsUtils.isConstantLiteralTrue(element.right) -> element.right
            else -> return false
        }
        val parent = element.eContainer() ?: return false
        EcoreUtil2.replace(element, replacement)
        worklist.add(parent)
        return true
    }
}

