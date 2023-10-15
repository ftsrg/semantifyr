package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.*
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

class InlineOperationEvaluator(
    private val context: InstanceObject
) {
    fun inlineTransition(operation: InlineOperation): Operation {
        return when (operation) {
            is InlineCall -> {
                val containerInstance = context.expressionEvaluator.evaluateInstanceObject(operation.reference.asChainReferenceExpression().dropLast(1))
                val transition = context.expressionEvaluator.evaluateTypedReference<Transition>(operation.reference)

                OxstsFactory.createChoiceOperation().apply {
                    for (currentOperation in transition.operation) {
                        this.operation += currentOperation
                            .copy()
                            .rewriteToContext(containerInstance, transition.parameters)
                            .rewriteToParameters(transition.parameters, operation.parameterBindings)
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
            val instanceReference = OxstsFactory.createChainReferenceExpression(instance.instance!!)
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

    private fun Operation.rewriteToContext(localContext: InstanceObject, parameters: List<Parameter>): Operation {
        val references = EcoreUtil2.getAllContents<EObject>(this, true).asSequence().filterIsInstance<ChainReferenceExpression>().filterNot {
            parameters.contains(it.chains.first().element)
        }.toList()

        for (reference in references) {
            reference.rewriteToContext(localContext)
        }

        return this
    }

    private fun ReferenceExpression.rewriteToContext(localContext: InstanceObject) {
        require(this is ChainReferenceExpression)

        val inlineComposite = EcoreUtil2.getContainerOfType(this, InlineComposite::class.java)

        if (inlineComposite != null && inlineComposite.transition == this) {
            return
        }

        chains.addAll(0, createReferenceToContext(localContext))
    }

    private fun createReferenceToContext(
        localContext: InstanceObject,
        context: MutableList<ChainingExpression> = mutableListOf()
    ): List<ChainingExpression> {
        val instance = localContext.instance ?: return emptyList()
        val parent = localContext.parent ?: error("Has instance, but no parent? Bug!")

        createReferenceToContext(parent, context)

        context += OxstsFactory.createChainingExpression(instance)

        return context
    }

}
