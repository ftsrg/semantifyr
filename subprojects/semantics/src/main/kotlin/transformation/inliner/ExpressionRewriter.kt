/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.inliner

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.ExpressionTypeEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.ImmutableTypeEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.domain.DomainMemberCalculator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Declaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParameterDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParametricDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SelfReference
import hu.bme.mit.semantifyr.semantics.utils.isReferenceContextual
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
import hu.bme.mit.semantifyr.semantics.utils.copy
import hu.bme.mit.semantifyr.semantics.utils.eAllOfType
import org.eclipse.xtext.EcoreUtil2

@Singleton
class ExpressionRewriter {

    @Inject
    private lateinit var domainMemberCalculator: DomainMemberCalculator

    @Inject
    private lateinit var expressionTypeEvaluatorProvider: ExpressionTypeEvaluatorProvider

    fun rewriteExpressionsToContext(rootElement: Element, newContext: Expression) {
        rewriteContextualExpressionsToContext(rootElement, newContext)
        // ORDER IS IMPORTANT!
        // Self-references must be rewritten only at the end, otherwise they will be rewritten twice
        rewriteSelfExpressionsToContext(rootElement, newContext)
    }

    fun rewriteExpressionsToCall(rootElement: Element, parametricDeclaration: ParametricDeclaration, callSuffixExpression: CallSuffixExpression) {
        val parameterReferences = rootElement.eAllOfType<ElementReference>().filter {
            it.element is ParameterDeclaration
        }.toList()

        for (parameterReference in parameterReferences) {
            val index = parametricDeclaration.parameters.indexOf(parameterReference.element)
            val argument = callSuffixExpression.arguments[index].expression.copy()

            EcoreUtil2.replace(parameterReference, argument)
            fixRedefinitions(argument)
        }
    }

    private fun rewriteContextualExpressionsToContext(rootElement: Element, newContext: Expression) {
        val contextualReferences = rootElement.eAllOfType<ElementReference>().filter {
            isReferenceContextual(it)
        }.toList()

        for (contextualReference in contextualReferences) {
            val newReference = OxstsFactory.createNavigationSuffixExpression().also {
                it.primary = newContext.copy()
                it.member = contextualReference.element
            }
            EcoreUtil2.replace(contextualReference, newReference)
            fixRedefinitions(newReference)
        }
    }

    fun rewriteReferencesTo(referencedElement: NamedElement, rootElement: Element, newContext: Expression) {
        val elementReferences = rootElement.eAllOfType<ElementReference>().filter {
            it.element == referencedElement
        }.toList()

        for (elementReference in elementReferences) {
            val context = newContext.copy()
            EcoreUtil2.replace(elementReference, context)
            fixRedefinitions(context)
        }
    }

    private tailrec fun fixRedefinitions(expression: Expression) {
        if (expression is NavigationSuffixExpression && expression.member is Declaration) {
            val member = expression.member
            if (member is Declaration) {
                val primaryType = expressionTypeEvaluatorProvider.evaluate(expression.primary)
                if (primaryType is ImmutableTypeEvaluation) {
                    val memberCollection = domainMemberCalculator.getMemberCollection(primaryType.domainDeclaration)
                    expression.member = memberCollection.resolveElement(member)
                }
            }
        }

        val parent = expression.eContainer()

        if (parent !is Expression) {
            return
        }

        return fixRedefinitions(parent)
    }

    private fun rewriteSelfExpressionsToContext(rootElement: Element, newContext: Expression) {
        val selfReferences = rootElement.eAllOfType<SelfReference>().toList()

        for (selfReference in selfReferences) {
            val context = newContext.copy()
            EcoreUtil2.replace(selfReference, context)
            fixRedefinitions(context)
        }
    }

}
