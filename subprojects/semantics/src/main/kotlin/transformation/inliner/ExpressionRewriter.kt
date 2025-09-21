/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.inliner

import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParameterDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SelfReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
import hu.bme.mit.semantifyr.semantics.utils.copy
import hu.bme.mit.semantifyr.semantics.utils.eAllOfType
import org.eclipse.xtext.EcoreUtil2

@Singleton
class ExpressionRewriter {

    fun rewriteExpressionsToContext(rootElement: Element, newContext: Expression) {
        rewriteContextualExpressionsToContext(rootElement, newContext)
        // ORDER IS IMPORTANT!
        // Self-references must be rewritten only at the end, otherwise they will be rewritten twice
        rewriteSelfExpressionsToContext(rootElement, newContext)
    }

    fun rewriteExpressionsToCall(rootElement: Element, transitionDeclaration: TransitionDeclaration, callSuffixExpression: CallSuffixExpression) {
        val parameterReferences = rootElement.eAllOfType<ElementReference>().filter {
            it.element is ParameterDeclaration
        }.toList()

        for (parameterReference in parameterReferences) {
            val index = transitionDeclaration.parameters.indexOf(parameterReference.element)
            val argument = callSuffixExpression.arguments[index].expression

            EcoreUtil2.replace(parameterReference, argument)
        }
    }

    private fun rewriteContextualExpressionsToContext(rootElement: Element, newContext: Expression) {
        val contextualReferences = rootElement.eAllOfType<ElementReference>().filter {
            it.element is FeatureDeclaration
            || it.element is VariableDeclaration
            || it.element is PropertyDeclaration
            || it.element is TransitionDeclaration
        }.toList()

        for (contextualReference in contextualReferences) {
            val newReference = OxstsFactory.createNavigationSuffixExpression().also {
                it.primary = newContext.copy()
                it.member = contextualReference.element
            }
            EcoreUtil2.replace(contextualReference, newReference)
        }
    }

    private fun rewriteSelfExpressionsToContext(rootElement: Element, newContext: Expression) {
        val selfReferences = rootElement.eAllOfType<SelfReference>().toList()

        for (selfReference in selfReferences) {
            EcoreUtil2.replace(selfReference, newContext.copy())
        }
    }

}
