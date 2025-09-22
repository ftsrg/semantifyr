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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineIfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.semantics.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.expression.StaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.expression.evaluateTyped
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

    fun inlineOperations(instance: Instance, transition: TransitionDeclaration) {
        val processorQueue = LinkedList<Operation>(transition.branches)

        while (processorQueue.any()) {
            val operation = processorQueue.removeFirst()

            when (operation) {
                is SequenceOperation -> processorQueue += operation.steps
                is ChoiceOperation -> {
                    processorQueue += operation.branches
                    processorQueue += operation.`else`
                }
                is ForOperation -> processorQueue += operation.body
                is IfOperation -> {
                    processorQueue += operation.body
                    processorQueue += operation.`else`
                }
                is InlineOperation -> {
                    val inlined = createInlinedOperation(instance, operation)
                    EcoreUtil2.replace(operation, inlined)

                    processorQueue += simplifyNestedInlinedSequence(inlined)

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
            is InlineForOperation -> createInlinedOperation(instance, operation)
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

        val metaEvaluator = metaStaticExpressionEvaluatorProvider.getEvaluator(instance)

        val transitionDeclaration = metaEvaluator.evaluateTyped(TransitionDeclaration::class.java, transitionReferenceExpression)

        val inlined = OxstsFactory.createChoiceOperation().also {
            for (currentOperation in transitionDeclaration.branches) {
                val inlinedOperation = currentOperation.copy()

                expressionRewriter.rewriteExpressionsToContext(inlinedOperation, containerInstanceReference)
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

    private fun createInlinedOperation(instance: Instance, operation: InlineForOperation): Operation {
//        val inlineCalls = inlineCallsFromComposite(operation)
//
//        OxstsFactory.createSequenceOperation().also {
//            it.operation += inlineCalls
//        }
//
//        val inlineCalls = inlineCallsFromComposite(operation)
//
//        OxstsFactory.createChoiceOperation().also {
//            it.operation += inlineCalls
//
//            if (operation.`else` != null) {
//                it.`else` = operation.`else`.copy()
//            }
//        }

        return OxstsFactory.createSequenceOperation()
    }

}
