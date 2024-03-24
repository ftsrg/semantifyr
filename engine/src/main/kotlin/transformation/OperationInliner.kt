package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.engine.utils.appendWith
import hu.bme.mit.gamma.oxsts.engine.utils.asChainReferenceExpression
import hu.bme.mit.gamma.oxsts.engine.utils.copy
import hu.bme.mit.gamma.oxsts.engine.utils.drop
import hu.bme.mit.gamma.oxsts.engine.utils.dropLast
import hu.bme.mit.gamma.oxsts.engine.utils.element
import hu.bme.mit.gamma.oxsts.engine.utils.isStaticReference
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainingExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.ContextDependentReference
import hu.bme.mit.gamma.oxsts.model.oxsts.InlineCall
import hu.bme.mit.gamma.oxsts.model.oxsts.InlineChoice
import hu.bme.mit.gamma.oxsts.model.oxsts.InlineComposite
import hu.bme.mit.gamma.oxsts.model.oxsts.InlineIfOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.InlineOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.InlineSeq
import hu.bme.mit.gamma.oxsts.model.oxsts.NothingReference
import hu.bme.mit.gamma.oxsts.model.oxsts.Operation
import hu.bme.mit.gamma.oxsts.model.oxsts.Parameter
import hu.bme.mit.gamma.oxsts.model.oxsts.ParameterBinding
import hu.bme.mit.gamma.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.SelfReference
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

class OperationInliner(
    private val context: Instance
) {
    fun inlineOperation(operation: InlineOperation): Operation {
        return when (operation) {
            is InlineCall -> {
                val containerInstance = context.expressionEvaluator.evaluateInstanceOrNull(operation.reference.asChainReferenceExpression().dropLast(1))

                if (containerInstance == null) {
                    // TODO: should we throw an exception here?
                    //  If the feature has no instances, then this is a violated reference
                    return OxstsFactory.createEmptyOperation()
                }

                val transition = context.expressionEvaluator.evaluateTransition(operation.reference)

                OxstsFactory.createChoiceOperation().apply {
                    for (currentOperation in transition.operation) {
                        this.operation += currentOperation.copy()
                            .rewriteInlineOperation(containerInstance, transition.parameters, operation.parameterBindings)
                    }
                }
            }
            is InlineIfOperation -> {
                if (context.expressionEvaluator.evaluateBoolean(operation.guard)) {
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
        val instanceSet = context.expressionEvaluator.evaluateInstanceSet(inlineComposite.feature)

        val transitionReference = inlineComposite.transition.asChainReferenceExpression()

        val list = mutableListOf<InlineCall>()

        for (instance in instanceSet) {
            val instanceReference = OxstsFactory.createChainReferenceExpression(createReferenceToContext(instance)).appendWith(transitionReference)
            val inlineCall = OxstsFactory.createInlineCall(instanceReference)

            list += inlineCall
        }

        return list
    }

    private fun Operation.rewriteToParameters(parameters: List<Parameter>, bindings: List<ParameterBinding>): Operation {
        val references = EcoreUtil2.getAllContents<EObject>(this, true).asSequence().filterIsInstance<ChainReferenceExpression>().filter {
            parameters.contains(it.chains.first().element)
        }.toList()

        for (reference in references) {
            val parameter = reference.chains.first().element
            val parameterIndex = parameters.indexOf(parameter)
            val binding = bindings[parameterIndex] // TODO is index based stable?
            val chain = binding.expression as ChainReferenceExpression
            val newExpression = chain.copy().appendWith(reference.drop(1))
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

    private fun Operation.rewriteToContext(localContext: Instance, parameters: List<Parameter>): Operation {
        val references = EcoreUtil2.getAllContentsOfType(this, ChainReferenceExpression::class.java).asSequence().filterNot {
            parameters.contains(it.chains.firstOrNull()?.element)
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
        parameters: List<Parameter>,
        bindings: List<ParameterBinding>
    ): Operation {
        return rewriteContextDependentReferences()
            .rewriteToContext(containerInstance, parameters)
            .rewriteToParameters(parameters, bindings)
    }

}
