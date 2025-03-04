/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.rewrite

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Argument
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArgumentBinding
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CompositeOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineCall
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineChoice
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineComposite
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineIfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineSeq
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Transition
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.rewrite.ExpressionRewriter.rewriteContextDependentReferences
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.rewrite.ExpressionRewriter.rewriteToContext
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.appendWith
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.asChainReferenceExpression
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.contextualEvaluator
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.copy
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.createReference
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.drop
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.dropLast
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.eAllContentsOfType
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.isStaticReference
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.operationInliner
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.referencedElement
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.referencedElementOrNull
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.typedReferencedElement
import org.eclipse.xtext.EcoreUtil2
import java.util.*

class OperationInliner(
    private val context: Instance
) {
    fun inlineOperation(operation: InlineOperation): Operation {
        return when (operation) {
            is InlineCall -> {
                val containerInstance = context.contextualEvaluator.evaluateInstanceOrNull(operation.reference.asChainReferenceExpression().dropLast(1))

                @Suppress("FoldInitializerAndIfToElvis")
                if (containerInstance == null) {
                    // TODO: should we throw an exception here?
                    //  If the feature has no instances, then this is a violated reference
                    return OxstsFactory.createEmptyOperation()
                }

                val transition = if (operation.isStatic) {
                    operation.reference.typedReferencedElement()
                } else {
                    context.contextualEvaluator.evaluateTransition(operation.reference)
                }

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
            val instanceReference = OxstsFactory.createChainReferenceExpression(instance.createReference()).appendWith(transitionReference)
            val inlineCall = OxstsFactory.createInlineCall(instanceReference)

            list += inlineCall
        }

        return list
    }

    private fun Operation.rewriteToArguments(arguments: List<Argument>, bindings: List<ArgumentBinding>): Operation {
        val references = eAllContents().asSequence().filterIsInstance<ChainReferenceExpression>().filter {
            arguments.contains(it.chains.firstOrNull()?.referencedElementOrNull())
        }.toList()

        for (reference in references) {
            val argument = reference.chains.first().referencedElement()
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


    private fun Operation.rewriteToContextSkipArguments(localContext: Instance, arguments: List<Argument>): Operation {
        val references = eAllContentsOfType<ChainReferenceExpression>().filterNot {
            arguments.contains(it.chains.firstOrNull()?.referencedElementOrNull())
        }.filterNot {
            it.isStaticReference
        }.filterNot {
            EcoreUtil2.getContainerOfType(it, InlineComposite::class.java)?.transition == it
        }.toList()

        for (reference in references) {
            reference.rewriteToContext(localContext)
        }

        return this
    }

    private fun Operation.rewriteInlineOperation(
        containerInstance: Instance,
        arguments: List<Argument>,
        bindings: List<ArgumentBinding>
    ): Operation {
        return rewriteContextDependentReferences()
            .rewriteToContextSkipArguments(containerInstance, arguments)
            .rewriteToArguments(arguments, bindings)
    }

}

fun Transition.inlineOperations(rootInstance: Instance) {
    val processorQueue = LinkedList(operation)

    while (processorQueue.any()) {
        val operation = processorQueue.removeFirst()

        when (operation) {
            is InlineOperation -> {
                val inlined = rootInstance.operationInliner.inlineOperation(operation)
                EcoreUtil2.replace(operation, inlined)
                processorQueue += inlined
            }
            is IfOperation -> {
                processorQueue += operation.body
                if (operation.`else` != null) {
                    processorQueue += operation.`else`
                }
            }
            is ChoiceOperation -> {
                processorQueue += operation.operation
                if (operation.`else` != null) {
                    processorQueue += operation.`else`
                }
            }
            is CompositeOperation -> {
                processorQueue += operation.operation
            }
        }
    }
}
