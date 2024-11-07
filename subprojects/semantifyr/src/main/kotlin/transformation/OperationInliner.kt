/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.transformation

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Argument
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArgumentBinding
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChainingExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ContextDependentReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineCall
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineChoice
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineComposite
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineIfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineSeq
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NothingReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SelfReference
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.appendWith
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.asChainReferenceExpression
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.contextualEvaluator
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.copy
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.drop
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.dropLast
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.element
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.isStaticReference
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

class OperationInliner(
    private val context: Instance
) {
    fun inlineOperation(operation: InlineOperation): Operation {
        return when (operation) {
            is InlineCall -> {
                val containerInstance = context.contextualEvaluator.evaluateInstanceOrNull(operation.reference.asChainReferenceExpression().dropLast(1))

                @Suppress("FoldInitializerAndIfToElvis") // would be difficult to read
                if (containerInstance == null) {
                    // TODO: should we throw an exception here?
                    //  If the feature has no instances, then this is a violated reference
                    return OxstsFactory.createEmptyOperation()
                }

                val transition = context.contextualEvaluator.evaluateTransition(operation.reference)

                OxstsFactory.createChoiceOperation().apply {
                    for (currentOperation in transition.operation) {
                        this.operation += currentOperation.copy()
                            .rewriteInlineOperation(containerInstance, transition.arguments, operation.argumentBindings)
                    }
                }
            }
            is InlineIfOperation -> {
                if (context.contextualEvaluator.evaluateBoolean(operation.guard)) {
                    operation.body.copy()
                } else {
                    operation.`else`?.copy() ?: OxstsFactory.createEmptyOperation()
                }
            }
            is InlineSeq -> {
                val inlineCalls = inlineCallsFromComposite(operation)

                OxstsFactory.createSequenceOperation().also {
                    it.operation += inlineCalls
                }
            }
            is InlineChoice -> {
                val inlineCalls = inlineCallsFromComposite(operation)

                OxstsFactory.createChoiceOperation().also {
                    it.operation += inlineCalls

                    if (operation.`else` != null) {
                        it.`else` = operation.`else`.copy()
                    }
                }
            }
            else -> error("Operation is not of known type: $operation")
        }
    }

    private fun inlineCallsFromComposite(inlineComposite: InlineComposite): List<InlineCall> {
        val instanceSet = context.contextualEvaluator.evaluateInstanceSet(inlineComposite.feature)

        val transitionReference = inlineComposite.transition.asChainReferenceExpression()

        val list = mutableListOf<InlineCall>()

        for (instance in instanceSet) {
            val instanceReference = OxstsFactory.createChainReferenceExpression(createReferenceToContext(instance)).appendWith(transitionReference)
            val inlineCall = OxstsFactory.createInlineCall(instanceReference)

            list += inlineCall
        }

        return list
    }

    private fun Operation.rewriteToArguments(arguments: List<Argument>, bindings: List<ArgumentBinding>): Operation {
        val references = EcoreUtil2.getAllContents<EObject>(this, true).asSequence().filterIsInstance<ChainReferenceExpression>().filter {
            arguments.contains(it.chains.first().element)
        }.toList()

        for (reference in references) {
            val argument = reference.chains.first().element
            val argumentIndex = arguments.indexOf(argument)
            val binding = bindings[argumentIndex] // TODO is index based stable?
            val expression = binding.expression
            val newExpression = if (expression is ChainReferenceExpression) {
                expression.copy().appendWith(reference.drop(1))
            } else {
                expression.copy()
            }
            EcoreUtil2.replace(reference, newExpression)
        }

        return this
    }

    private fun Operation.rewriteContextDependentReferences(): Operation {
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

    private fun Operation.rewriteToContext(localContext: Instance, arguments: List<Argument>): Operation {
        val references = EcoreUtil2.getAllContentsOfType(this, ChainReferenceExpression::class.java).asSequence().filterNot {
            arguments.contains(it.chains.firstOrNull()?.element)
        }.filterNot {
            it.isStaticReference
        }.toList()

        for (reference in references) {
            reference.rewriteToContext(localContext)
        }

        return this
    }

    private fun ReferenceExpression.rewriteToContext(localContext: Instance) {
        require(this is ChainReferenceExpression)

        val inlineComposite = EcoreUtil2.getContainerOfType(this, InlineComposite::class.java)

        if (inlineComposite?.transition == this) {
            return
        }

        chains.addAll(0, createReferenceToContext(localContext))
    }

    private fun createReferenceToContext(
        localContext: Instance,
        context: MutableList<ChainingExpression> = mutableListOf()
    ): List<ChainingExpression> {
        val containment = localContext.containment
        val parent = localContext.parent ?: return emptyList()

        createReferenceToContext(parent, context)

        context += OxstsFactory.createDeclarationReference(containment)

        return context
    }

    private fun Operation.rewriteInlineOperation(
        containerInstance: Instance,
        arguments: List<Argument>,
        bindings: List<ArgumentBinding>
    ): Operation {
        return rewriteContextDependentReferences()
            .rewriteToContext(containerInstance, arguments)
            .rewriteToArguments(arguments, bindings)
    }

}
