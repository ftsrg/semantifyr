/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.inliner

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.utils.OperationVisitor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.GuardOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineCall
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineChoiceFor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineIfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineSeqFor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.semantics.expression.InstanceReferenceProvider
import hu.bme.mit.semantifyr.semantics.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.expression.RedefinitionAwareReferenceResolver
import hu.bme.mit.semantifyr.semantics.expression.StaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.expression.evaluateTyped
import hu.bme.mit.semantifyr.semantics.transformation.serializer.CompilationStateManager
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
import hu.bme.mit.semantifyr.semantics.utils.allBranches
import hu.bme.mit.semantifyr.semantics.utils.copy
import hu.bme.mit.semantifyr.semantics.utils.eAllOfType
import org.eclipse.xtext.EcoreUtil2

class OperationCallInliner : OperationVisitor<Unit>() {

    lateinit var instance: Instance

    @Inject
    private lateinit var expressionCallInliner: ExpressionCallInliner

    @Inject
    private lateinit var staticExpressionEvaluatorProvider: StaticExpressionEvaluatorProvider

    @Inject
    private lateinit var metaStaticExpressionEvaluatorProvider: MetaStaticExpressionEvaluatorProvider

    @Inject
    private lateinit var expressionRewriter: ExpressionRewriter

    @Inject
    private lateinit var compilationStateManager: CompilationStateManager

    @Inject
    private lateinit var instanceReferenceProvider: InstanceReferenceProvider

    @Inject
    private lateinit var redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver

    private var localVariableIndex: Int = 0

    private val processorQueue = ArrayDeque<Operation>()

    fun process(operation: Operation) {
        expressionCallInliner.instance = instance
        visit(operation)
        processAll()
    }

    private fun processAll() {
        while (processorQueue.any()) {
            val next = processorQueue.removeFirst()
            visit(next)
        }
    }

    override fun visit(operation: SequenceOperation) {
        for (index in operation.steps.indices.reversed()) {
            processorQueue.addFirst(operation.steps[index])
        }
    }

    override fun visit(operation: ChoiceOperation) {
        for (index in operation.allBranches.indices.reversed()) {
            processorQueue.addFirst(operation.allBranches[index])
        }
    }

    override fun visit(operation: LocalVarDeclarationOperation) {
        expressionCallInliner.process(operation.expression)
    }

    override fun visit(operation: ForOperation) {
        expressionCallInliner.process(operation.rangeExpression)
        processorQueue.addFirst(operation.body)
    }

    override fun visit(operation: IfOperation) {
        expressionCallInliner.process(operation.guard)
        processorQueue.addFirst(operation.body)
        if (operation.`else` != null) {
            processorQueue.addFirst(operation.`else`)
        }
    }

    override fun visit(operation: HavocOperation) {
        // NO-OP
    }

    override fun visit(operation: AssumptionOperation) {
        expressionCallInliner.process(operation.expression)
    }

    override fun visit(operation: GuardOperation) {
        expressionCallInliner.process(operation.expression)
    }

    override fun visit(operation: AssignmentOperation) {
        expressionCallInliner.process(operation.expression)
    }

    override fun visit(operation: InlineCall) {
        performInlining(operation)
    }

    override fun visit(operation: InlineIfOperation) {
        performInlining(operation)
    }

    override fun visit(operation: InlineSeqFor) {
        performInlining(operation)
    }

    override fun visit(operation: InlineChoiceFor) {
        performInlining(operation)
    }

    private fun performInlining(operation: InlineOperation) {
        val inlined = createInlinedOperation(instance, operation)
        rewriteLocalVariables(inlined)
        EcoreUtil2.replace(operation, inlined)

        val actualInlined = simplifyInlinedOperation(inlined)
        for (index in actualInlined.indices.reversed()) {
            processorQueue.addFirst(actualInlined[index])
        }

        compilationStateManager.commitModelState()
    }

    private fun rewriteLocalVariables(operation: Operation) {
        val localVars = operation.eAllOfType<LocalVarDeclarationOperation>().toList()

        for (localVar in localVars) {
            localVar.name += "$${localVariableIndex++}"
        }
    }

    private fun simplifyNestedSequence(operation: Operation): Boolean {
        // TODO: this is duplicated from OperationFlattenerOptimizer -> should probably merge
        val sequenceOperation = operation.eAllOfType<SequenceOperation>().firstOrNull {
            it.steps.filterIsInstance<SequenceOperation>().any()
        }

        if (sequenceOperation == null) {
            return false
        }

        val nestedSequenceOperation = sequenceOperation.steps.filterIsInstance<SequenceOperation>().first()

        val index = sequenceOperation.steps.indexOf(nestedSequenceOperation)
        sequenceOperation.steps.addAll(index, nestedSequenceOperation.steps)

        EcoreUtil2.remove(nestedSequenceOperation)

        return true
    }

    private fun simplifyNestedOperation(operation: Operation) {
        var anyProgress = true

        while (anyProgress) {
            anyProgress = simplifyNestedSequence(operation)
        }
    }

    private fun simplifyInlinedOperation(inlinedOperation: Operation): List<Operation> {
        simplifyNestedOperation(inlinedOperation)

        val parent = inlinedOperation.eContainer()

        if (parent !is SequenceOperation || inlinedOperation !is SequenceOperation) {
            return listOf(inlinedOperation)
        }

        val index = parent.steps.indexOf(inlinedOperation)
        val internalInlined = ArrayList(inlinedOperation.steps)
        parent.steps.addAll(index, internalInlined)
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
        val callExpression = operation.callExpression as CallSuffixExpression

        val metaEvaluator = metaStaticExpressionEvaluatorProvider.getEvaluator(instance)
        val evaluator = staticExpressionEvaluatorProvider.getEvaluator(instance)
        val transitionReferenceExpression = callExpression.primary

        val containerInstanceReference = if (transitionReferenceExpression is NavigationSuffixExpression) {
            val transitionHolder = metaEvaluator.evaluate(transitionReferenceExpression.primary)
            check(transitionHolder !is VariableDeclaration) {
                "Variable dispatching is not supported yet!"
            }

            transitionReferenceExpression.primary
        } else {
            OxstsFactory.createSelfReference()
        }

        val transitionDeclaration = metaEvaluator.evaluateTyped(TransitionDeclaration::class.java, transitionReferenceExpression)
        val containerInstance = evaluator.evaluateSingleInstance(containerInstanceReference)
        return inlineTransitionCall(containerInstance, transitionDeclaration, callExpression)
    }

    private fun inlineTransitionCall(containerInstance: Instance, transitionDeclaration: TransitionDeclaration, callExpression: CallSuffixExpression): Operation {
        val containerInstanceReference = instanceReferenceProvider.getReference(containerInstance)

        val actualTransition = redefinitionAwareReferenceResolver.resolve(containerInstance, transitionDeclaration) as TransitionDeclaration

        val inlined = OxstsFactory.createChoiceOperation().also {
            for (currentOperation in actualTransition.branches) {
                val inlinedOperation = currentOperation.copy()

                expressionRewriter.rewriteExpressionsToContext(inlinedOperation, containerInstanceReference)
                expressionRewriter.rewriteExpressionsToCall(inlinedOperation, actualTransition, callExpression)

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
            val instanceReference = instanceReferenceProvider.getReference(featureInstance)
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
            val instanceReference = instanceReferenceProvider.getReference(featureInstance)
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
