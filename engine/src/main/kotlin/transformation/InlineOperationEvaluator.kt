package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.engine.utils.appendWith
import hu.bme.mit.gamma.oxsts.engine.utils.asChainReferenceExpression
import hu.bme.mit.gamma.oxsts.engine.utils.copy
import hu.bme.mit.gamma.oxsts.engine.utils.drop
import hu.bme.mit.gamma.oxsts.engine.utils.dropLast
import hu.bme.mit.gamma.oxsts.engine.utils.element
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
import hu.bme.mit.gamma.oxsts.model.oxsts.Transition
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

class InlineOperationEvaluator(
    private val context: InstanceObject
) {
    fun inlineOperation(operation: InlineOperation): Operation {
        return when (operation) {
            is InlineCall -> {
                val containerInstance = context.expressionEvaluator.evaluateInstanceObjectOrNull(operation.reference.asChainReferenceExpression().dropLast(1))

                if (containerInstance == null) {
                    return OxstsFactory.createEmptyOperation()
                }

                val transition = context.expressionEvaluator.evaluateTransition(operation.reference)

                OxstsFactory.createChoiceOperation().apply {
                    for (currentOperation in transition.operation) {
                        this.operation += currentOperation
                            .copy()
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
        val instanceSet = context.expressionEvaluator.evaluateInstanceObjectSet(inlineComposite.feature)

        val baseFeature = inlineComposite.feature.asChainReferenceExpression().dropLast(1)
        val transitionReference = inlineComposite.transition.asChainReferenceExpression()

        val list = mutableListOf<InlineCall>()

        for (instance in instanceSet) {
            val instanceReference = OxstsFactory.createChainReferenceExpression(instance.instanceHolder!!)
            val inlineCall = OxstsFactory.createInlineCall(baseFeature.appendWith(instanceReference).appendWith(transitionReference))

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
                else -> error("")
            }
        }

        return this
    }

    private fun Operation.rewriteToContext(localContext: InstanceObject, parameters: List<Parameter>): Operation {
        val references = EcoreUtil2.getAllContentsOfType(this, ChainReferenceExpression::class.java).asSequence().filterNot {
            parameters.contains(it.chains.firstOrNull()?.element)
        }.toList()

        for (reference in references) {
            reference.rewriteToContext(localContext)
        }

        return this
    }

    private fun ReferenceExpression.rewriteToContext(localContext: InstanceObject) {
        require(this is ChainReferenceExpression)

        val inlineComposite = EcoreUtil2.getContainerOfType(this, InlineComposite::class.java)

        if (inlineComposite?.transition == this) {
            return
        }

        chains.addAll(0, createReferenceToContext(localContext))
    }

    private fun createReferenceToContext(
        localContext: InstanceObject,
        context: MutableList<ChainingExpression> = mutableListOf()
    ): List<ChainingExpression> {
        val instance = localContext.instanceHolder ?: return emptyList()
        val parent = localContext.parent ?: return emptyList()

        createReferenceToContext(parent, context)

        context += OxstsFactory.createDeclarationReference(instance)

        return context
    }

    private fun Operation.rewriteInlineOperation(
        containerInstance: InstanceObject,
        parameters: List<Parameter>,
        bindings: List<ParameterBinding>
    ) = rewriteContextDependentReferences().rewriteToContext(containerInstance, parameters).rewriteToParameters(parameters, bindings)

}
