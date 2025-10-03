/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.inliner

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineCall
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineChoiceFor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineIfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineSeqFor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.semantics.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.expression.StaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.expression.evaluateTyped
import hu.bme.mit.semantifyr.semantics.transformation.instantiation.InstanceManager
import hu.bme.mit.semantifyr.semantics.transformation.serializer.CompilationArtifactSaver
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
import hu.bme.mit.semantifyr.semantics.utils.copy
import org.eclipse.xtext.EcoreUtil2
import java.util.*

@Singleton
class OperationInliner {

    @Inject
    private lateinit var staticExpressionEvaluatorProvider: StaticExpressionEvaluatorProvider

    @Inject
    private lateinit var metaStaticExpressionEvaluatorProvider: MetaStaticExpressionEvaluatorProvider

    @Inject
    private lateinit var expressionRewriter: ExpressionRewriter

    @Inject
    private lateinit var compilationArtifactSaver: CompilationArtifactSaver

    @Inject
    private lateinit var instanceManager: InstanceManager

    fun inlineOperations(instance: Instance, transition: TransitionDeclaration) {
        val processorQueue = LinkedList<Operation>(transition.branches)

        while (processorQueue.any()) {
            val operation: Operation? = processorQueue.removeFirst()

            when (operation) {
                is SequenceOperation -> processorQueue.addAll(0, operation.steps)
                is ChoiceOperation -> {
                    processorQueue.add(0, operation.`else`)
                    processorQueue.addAll(0, operation.branches)
                }
                is ForOperation -> processorQueue.add(0, operation.body)
                is IfOperation -> {
                    processorQueue.add(0, operation.`else`)
                    processorQueue.add(0, operation.body)
                }
                is InlineOperation -> {
                    val inlined = createInlinedOperation(instance, operation)
                    EcoreUtil2.replace(operation, inlined)

                    processorQueue.addAll(0, simplifyNestedInlinedSequence(inlined))

                    compilationArtifactSaver.commitModelState()
                }
            }
        }
    }

    private fun simplifyNestedInlinedSequence(inlinedOperation: Operation): List<Operation> {
        val parent = inlinedOperation.eContainer()
        if (inlinedOperation !is SequenceOperation || parent !is SequenceOperation) {
            return listOf(inlinedOperation)
        }

        val internalInlined = ArrayList(inlinedOperation.steps)

        val index = parent.steps.indexOf(inlinedOperation)
        parent.steps.addAll(index, inlinedOperation.steps)
        EcoreUtil2.remove(inlinedOperation)

        return internalInlined
    }

    private fun createInlinedOperation(instance: Instance, operation: InlineOperation): Operation {
        return when (operation) {
            is InlineCall -> createInlinedOperation(instance, operation)
            is InlineIfOperation -> createInlinedOperation(instance, operation)
            is InlineSeqFor -> createInlinedOperation(instance, operation)
            is InlineChoiceFor -> createInlinedOperation(instance, operation)
            else -> error("Operation is not of known type: $operation")
        }
    }

    private fun createInlinedOperation(instance: Instance, operation: InlineCall): Operation {
        val evaluator = staticExpressionEvaluatorProvider.getEvaluator(instance)

        val callExpression = operation.callExpression as CallSuffixExpression
        val transitionReferenceExpression = callExpression.primary

        val containerInstanceReference = if (transitionReferenceExpression is NavigationSuffixExpression) {
            check(transitionReferenceExpression.primary !is VariableDeclaration) {
                "Variable dispatching is not supported yet!"
            }

            transitionReferenceExpression.primary
        } else {
            OxstsFactory.createSelfReference()
        }

        val containerInstance = evaluator.evaluateSingleInstanceOrNull(containerInstanceReference)

        @Suppress("FoldInitializerAndIfToElvis")
        if (containerInstance == null) {
            // TODO: should we throw an exception here?
            //  If the feature has no instances, then this is a violated reference
            return OxstsFactory.createSequenceOperation()
        }

        val actualContainerInstanceReference = instanceManager.createReferenceExpression(containerInstance)

        val metaEvaluator = metaStaticExpressionEvaluatorProvider.getEvaluator(instance)

        val transitionDeclaration = metaEvaluator.evaluateTyped(TransitionDeclaration::class.java, transitionReferenceExpression)

        val inlined = OxstsFactory.createChoiceOperation().also {
            for (currentOperation in transitionDeclaration.branches) {
                val inlinedOperation = currentOperation.copy()

                expressionRewriter.rewriteExpressionsToContext(inlinedOperation, actualContainerInstanceReference)
                expressionRewriter.rewriteExpressionsToCall(inlinedOperation, transitionDeclaration, callExpression)

                it.branches += inlinedOperation
            }
        }

        if (inlined.branches.size == 1) {
            return inlined.branches.single()
        }

        return inlined
    }

    private fun createInlinedOperation(instance: Instance, operation: InlineIfOperation): Operation {
        val evaluator = staticExpressionEvaluatorProvider.getEvaluator(instance)

        if (evaluator.evaluateBoolean(operation.guard)) {
            return operation.body.copy()
        }

        if (operation.`else` != null) {
            return operation.`else`.copy()
        }

        return OxstsFactory.createSequenceOperation()
    }

    private fun createInlinedOperation(instance: Instance, operation: InlineSeqFor): Operation {
        val evaluator = staticExpressionEvaluatorProvider.getEvaluator(instance)

        val featureInstances = evaluator.evaluateInstances(operation.rangeExpression)

        val inlinedFor = OxstsFactory.createSequenceOperation()

        for (featureInstance in featureInstances) {
            val instanceReference = instanceManager.createReferenceExpression(featureInstance)
            val inlinedBody = operation.body.copy()
            expressionRewriter.rewriteReferencesTo(operation.loopVariable, inlinedBody, instanceReference)
            inlinedFor.steps += inlinedBody
        }

        if (inlinedFor.steps.size == 1) {
            return inlinedFor.steps.single()
        }

        return inlinedFor
    }

    private fun createInlinedOperation(instance: Instance, operation: InlineChoiceFor): Operation {
        val evaluator = staticExpressionEvaluatorProvider.getEvaluator(instance)

        val featureInstances = evaluator.evaluateInstances(operation.rangeExpression)

        val inlinedChoice = OxstsFactory.createChoiceOperation()

        for (featureInstance in featureInstances) {
            val instanceReference = instanceManager.createReferenceExpression(featureInstance)
            val inlinedBody = operation.body.copy()
            expressionRewriter.rewriteReferencesTo(operation.loopVariable, inlinedBody, instanceReference)
            inlinedChoice.branches += inlinedBody
        }

        if (operation.`else` != null) {
            inlinedChoice.`else` = operation.`else`.copy()
        }

        if (inlinedChoice.branches.size == 1 && inlinedChoice.`else` == null) {
            return inlinedChoice.branches.single()
        }

        return inlinedChoice
    }

}
