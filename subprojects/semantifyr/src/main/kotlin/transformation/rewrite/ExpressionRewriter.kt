/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.rewrite

import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ContextDependentReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DeclarationReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EqualityOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Feature
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InequalityOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NothingReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OperatorExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SelfReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Variable
import hu.bme.mit.semantifyr.oxsts.model.oxsts.XSTS
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.evaluation.BooleanData
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.evaluation.IntegerData
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.contextualEvaluator
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.createReference
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.dropLast
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.isFeatureTyped
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.lastChain
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.referencedElement
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.referencedElementOrNull
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.variableTransformer
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

object ExpressionRewriter {

    fun <T : EObject> T.rewriteContextDependentReferences(): T {
        val references = EcoreUtil2.getAllContentsOfType(this, ContextDependentReference::class.java)

        for (reference in references) {
            when (reference) {
                is SelfReference -> {
                    EcoreUtil2.delete(reference)
                }
                is NothingReference -> {}
                else -> error("Should not have other kind of reference")
            }
        }

        return this
    }

    fun ReferenceExpression.rewriteToContext(localContext: Instance) {
        require(this is ChainReferenceExpression)

        chains.addAll(0, localContext.createReference())
    }

    fun XSTS.rewriteReferences(rootInstance: Instance) {
        rewriteFeatureTypedOperatorAccess(rootInstance)
        rewriteVariableAccesses(rootInstance)
        evaluateFeatureReferences(rootInstance)
    }

    fun XSTS.rewriteVariableAccesses(rootInstance: Instance) {
        val referenceExpressions = EcoreUtil2.getAllContentsOfType(this, ChainReferenceExpression::class.java).filter {
            val declaration = it.chains.last() as? DeclarationReferenceExpression
            declaration?.element is Variable
        }

        for (referenceExpression in referenceExpressions) {
            val reference = referenceExpression.chains.last() as DeclarationReferenceExpression
            val oldVariable = reference.element as Variable

            val instance = rootInstance.contextualEvaluator.evaluateInstance(referenceExpression.dropLast(1))
            val transformedVariable = instance.variableTransformer.findTransformedVariable(oldVariable)

            val newExpression = OxstsFactory.createChainReferenceExpression(transformedVariable)
            EcoreUtil2.replace(referenceExpression, newExpression)
        }
    }

    private fun XSTS.evaluateFeatureReferences(rootInstance: Instance) {
        val references = EcoreUtil2.getAllContentsOfType(this, ChainReferenceExpression::class.java).filter {
            it.lastChain().referencedElementOrNull() is Feature
        }

        for (reference in references) {
            val evaluation = rootInstance.contextualEvaluator.evaluate(reference)

            val evaluatedExpression = when (evaluation) {
                is BooleanData -> OxstsFactory.createLiteralBoolean(evaluation.value)
                is IntegerData -> OxstsFactory.createLiteralInteger(evaluation.value)
                else -> error("Feature reference is not an XSTS-compatible expression type!")
            }

            EcoreUtil2.replace(reference, evaluatedExpression)
        }
    }

    private fun XSTS.rewriteFeatureTypedOperatorAccess(rootInstance: Instance) {
        val expressions = EcoreUtil2.getAllContentsOfType(this, ChainReferenceExpression::class.java).filter {
            (it.lastChain().referencedElementOrNull() as? Variable)?.isFeatureTyped == true
        }

        for (expression in expressions) {
            val oldVariable = expression.lastChain().referencedElement() as Variable

            rewriteFeatureTypedOperatorAccess(oldVariable, expression, rootInstance)
        }
    }

    private fun rewriteFeatureTypedOperatorAccess(variable: Variable, referenceExpression: ChainReferenceExpression, rootInstance: Instance) {
        val parent = referenceExpression.eContainer()

        when (parent) {
            is AssignmentOperation -> {
                if (parent.reference != referenceExpression) return

                val newExpression = rootInstance.variableTransformer.transformExpression(parent.reference, parent.expression as ReferenceExpression, variable)

                EcoreUtil2.replace(parent.expression, newExpression)
            }
            is EqualityOperator -> {
                rewriteFeatureTypedOperatorAccess(parent, variable, referenceExpression, rootInstance)
            }
            is InequalityOperator -> {
                rewriteFeatureTypedOperatorAccess(parent, variable, referenceExpression, rootInstance)
            }
            else -> error("Unknown reference: $parent")
        }
    }

    private fun rewriteFeatureTypedOperatorAccess(operator: OperatorExpression, variable: Variable, referenceExpression: ChainReferenceExpression, rootInstance: Instance) {
        val operandIndex = operator.operands.indexOf(referenceExpression)

        val otherOperandIndex = if (operandIndex == 0) 1 else 0
        val otherOperand = operator.operands[otherOperandIndex]!! as ReferenceExpression

        val newExpression = rootInstance.variableTransformer.transformExpression(referenceExpression, otherOperand, variable)

        EcoreUtil2.replace(otherOperand, newExpression)
    }

}
