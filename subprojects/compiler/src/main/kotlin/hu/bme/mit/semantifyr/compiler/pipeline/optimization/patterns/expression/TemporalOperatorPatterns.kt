/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Worklist
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NegationOperator
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

class BubbleNotAGPattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is NegationOperator) return false
        val ag = element.body as? AG ?: return false
        val parent = element.eContainer() ?: return false

        val replacement = OxstsFactory.createEF().also {
            it.body = OxstsFactory.createNegationOperator().also { neg -> neg.body = ag.body }
        }
        EcoreUtil2.replace(element, replacement)
        worklist.add(replacement)
        worklist.add(parent)
        return true
    }
}

class BubbleNotEFPattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is NegationOperator) return false
        val ef = element.body as? EF ?: return false
        val parent = element.eContainer() ?: return false

        val replacement = OxstsFactory.createAG().also {
            it.body = OxstsFactory.createNegationOperator().also { neg -> neg.body = ef.body }
        }
        EcoreUtil2.replace(element, replacement)
        worklist.add(replacement)
        worklist.add(parent)
        return true
    }
}
