/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.rewrite

import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Property
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.asChainReferenceExpression
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.contextualEvaluator
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.copy
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.createReference
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.dropLast
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.eAllContentsOfType
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.lastChain
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.referencedElementOrNull
import org.eclipse.xtext.EcoreUtil2

object PropertyAccessInliner {

    fun Element.inlinePropertyAccesses(rootInstance: Instance) {
        var changed = true

        while (changed) {
            changed = inlinePropertyExpression(rootInstance)
        }
    }

    private fun Element.inlinePropertyExpression(rootInstance: Instance): Boolean {
        val propertyReference = eAllContentsOfType<ChainReferenceExpression>().firstOrNull {
            it.lastChain().referencedElementOrNull() is Property
        }

        if (propertyReference == null) {
            return false
        }

        val containerInstance = rootInstance.contextualEvaluator.evaluateInstance(propertyReference.asChainReferenceExpression().dropLast(1))
        val property = rootInstance.contextualEvaluator.evaluateProperty(propertyReference).copy()

        property.rewriteToContext(containerInstance)

        val expression = property.invariant

        EcoreUtil2.replace(propertyReference, expression)

        return true
    }

    private fun Element.rewriteToContext(context: Instance) {
        val chainReferenceExpressions = eAllContentsOfType<ChainReferenceExpression>().toList()

        for (reference in chainReferenceExpressions) {
            reference.chains.addAll(0, context.createReference())
        }
    }
}
