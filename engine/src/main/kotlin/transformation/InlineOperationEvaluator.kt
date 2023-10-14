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
                val containerInstance = context.expressionEvaluator.evaluateInstanceObject(operation.reference.asChainReferenceExpression().exceptLast())
                val transition = context.expressionEvaluator.evaluateTypedReference<Transition>(operation.reference)

                OxstsFactory.createChoiceOperation().apply {
                    for (currentOperation in transition.operation) {
                        this.operation.add(currentOperation.copyAndRewrite(containerInstance))
                    }
                }
            }
            is InlineIfOperation -> {
                if (context.expressionEvaluator.evaluateBoolean(operation.guard)) {
                    operation.body.copyAndRewrite(context)
                } else {
                    operation.`else`?.copyAndRewrite(context) ?: OxstsFactory.createEmptyOperation()
                }
            }
            is InlineSeq -> {
                val inlineCalls = inlineCallsFromComposite(operation)

                OxstsFactory.createSequenceOperation().also {
                    it.operation.addAll(inlineCalls)
                }
            }
            is InlineChoice -> {
                val inlineCalls = inlineCallsFromComposite(operation)

                OxstsFactory.createChoiceOperation().also {
                    it.operation.addAll(inlineCalls)
                }
            }
            else -> error("Operation is not of known type: $operation")
        }
    }

    private fun inlineCallsFromComposite(inlineComposite: InlineComposite): List<InlineCall> {
        val instanceSet = context.expressionEvaluator.evaluateInstanceObjectSet(inlineComposite.feature)

        val baseFeature = inlineComposite.feature.asChainReferenceExpression().exceptLast()
        val transitionReference = inlineComposite.transition.asChainReferenceExpression()

        val list = mutableListOf<InlineCall>()

        for (instance in instanceSet) {
            val instanceReference = OxstsFactory.createChainReferenceExpression(instance.instance!!)
            val inlineCall = OxstsFactory.createInlineCall(baseFeature.appendWith(instanceReference).appendWith(transitionReference))

            list.add(inlineCall)
        }

        return list
    }

    private fun Operation.copyAndRewrite(localContext: InstanceObject): Operation {
        val operation = copy()

        val references = EcoreUtil2.getAllContents<EObject>(operation, true).asSequence().filterIsInstance<ReferenceExpression>().toList()

        for (reference in references) {
            reference.rewrite(localContext)
        }

        return operation
    }

    private fun ReferenceExpression.rewrite(localContext: InstanceObject) {
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

        context.add(OxstsFactory.createChainingExpression(instance))

        return context
    }

}
