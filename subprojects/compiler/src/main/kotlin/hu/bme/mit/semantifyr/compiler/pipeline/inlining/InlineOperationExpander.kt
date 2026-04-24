/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.TransitionCallTraceBuilder
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceEvaluation
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceReferenceProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.RedefinitionAwareReferenceResolver
import hu.bme.mit.semantifyr.compiler.pipeline.expression.CompileTimeExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.pipeline.utils.sourceError
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.RangeEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineCall
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineChoiceFor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineIfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineSeqFor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration

class InlineOperationExpander @Inject constructor(
    private val compileTimeExpressionEvaluatorProvider: CompileTimeExpressionEvaluatorProvider,
    private val expressionRewriter: ExpressionRewriter,
    private val instanceReferenceProvider: InstanceReferenceProvider,
    private val redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver,
    private val builtinAnnotationHandler: BuiltinAnnotationHandler,
    private val transitionCallTracer: TransitionCallTraceBuilder,
    private val callTargetResolver: CallTargetResolver,
) {

    fun expand(operation: InlineOperation, instance: Instance, allocateLocalVarIndex: () -> Int): Operation {
        return when (operation) {
            is InlineCall -> expandCall(operation, instance, allocateLocalVarIndex)
            is InlineIfOperation -> expandIf(operation, instance)
            is InlineSeqFor -> expandSeqFor(operation, instance)
            is InlineChoiceFor -> expandChoiceFor(operation, instance)
            else -> sourceError(operation, "Unknown inline operation kind: ${operation::class.simpleName}")
        }
    }

    private fun expandCall(operation: InlineCall, instance: Instance, allocateLocalVarIndex: () -> Int): Operation {
        val callExpression = operation.callExpression as CallSuffixExpression

        return when (val target = callTargetResolver.resolve(callExpression, instance)) {
            is CallTarget.DirectInstance -> inlineTransitionCall(
                instance,
                target.containerInstance,
                transitionDeclarationOf(target.targetDeclaration, callExpression),
                callExpression,
                allocateLocalVarIndex,
            )
            is CallTarget.VariableDispatch -> dispatchOverVariable(
                operation,
                transitionDeclarationOf(target.targetDeclaration, callExpression),
                callExpression,
                instance,
                target,
                allocateLocalVarIndex,
            )
            is CallTarget.MissingOptional -> OxstsFactory.createSequenceOperation()
        }
    }

    private fun transitionDeclarationOf(
        targetDeclaration: NamedElement,
        callExpression: CallSuffixExpression,
    ): TransitionDeclaration {
        return targetDeclaration as? TransitionDeclaration ?: sourceError(
            callExpression,
            "Expected a transition declaration at call site, got ${targetDeclaration::class.simpleName}",
        )
    }

    private fun dispatchOverVariable(
        operation: InlineCall,
        transitionDeclaration: TransitionDeclaration,
        callExpression: CallSuffixExpression,
        instance: Instance,
        target: CallTarget.VariableDispatch,
        allocateLocalVarIndex: () -> Int,
    ): Operation {
        if (target.candidateInstances.isEmpty()) {
            sourceError(
                callExpression,
                "Variable '${target.variable.name}' has no candidate instances to dispatch the call over.",
            )
        }

        val dispatch = OxstsFactory.createChoiceOperation()
        for (candidate in target.candidateInstances) {
            val candidateReference = instanceReferenceProvider.getReference(candidate)
            val inlinedBranch = inlineTransitionCall(
                instance = instance,
                containerInstance = candidate,
                transitionDeclaration = transitionDeclaration,
                callExpression = callExpression,
                allocateLocalVarIndex = allocateLocalVarIndex,
            )
            val guardedBranch = OxstsFactory.createSequenceOperation().also {
                it.steps += OxstsFactory.createAssumptionOperation().also { assume ->
                    assume.expression = OxstsFactory.createComparisonOperator().also { cmp ->
                        cmp.op = ComparisonOp.EQ
                        cmp.left = target.containerReferenceExpression.copy()
                        cmp.right = candidateReference
                    }
                }
                it.steps += inlinedBranch
            }
            dispatch.branches += guardedBranch
        }
        return dispatch
    }

    private fun inlineTransitionCall(
        instance: Instance,
        containerInstance: Instance,
        transitionDeclaration: TransitionDeclaration,
        callExpression: CallSuffixExpression,
        allocateLocalVarIndex: () -> Int,
    ): Operation {
        val containerInstanceReference = instanceReferenceProvider.getReference(containerInstance)

        val actualTransition = redefinitionAwareReferenceResolver.resolve(
            containerInstance.domain,
            transitionDeclaration,
        ) as TransitionDeclaration

        if (actualTransition.isAbstract) {
            sourceError(transitionDeclaration, "Abstract transition can not be inlined!")
        }

        val inlined = OxstsFactory.createChoiceOperation().also {
            for (currentOperation in actualTransition.branches) {
                val inlinedOperation = currentOperation.copy()

                expressionRewriter.rewriteExpressionsToContext(inlinedOperation, containerInstanceReference)
                expressionRewriter.rewriteExpressionsToCall(inlinedOperation, actualTransition, callExpression)

                it.branches += inlinedOperation
            }
        }

        rewriteLocalVariables(inlined, allocateLocalVarIndex)

        val tracerOperation = if (builtinAnnotationHandler.isTransitionTraced(actualTransition)) {
            transitionCallTracer.traceTransitionCall(instance, containerInstance, actualTransition, callExpression)
        } else {
            null
        }

        if (inlined.branches.size == 1) {
            val singleBranch = inlined.branches.single()
            if (tracerOperation != null) {
                singleBranch.steps.addFirst(tracerOperation.copy())
            }
            return singleBranch
        }

        if (tracerOperation != null) {
            for (branch in inlined.branches) {
                branch.steps.addFirst(tracerOperation.copy())
            }
        }

        return inlined
    }

    private fun rewriteLocalVariables(operation: Operation, allocateLocalVarIndex: () -> Int) {
        val localVars = operation.eAllOfType<LocalVarDeclarationOperation>().toList()
        for (localVar in localVars) {
            localVar.name = LocalVarNames.inlinedName(localVar, allocateLocalVarIndex())
        }
    }

    private fun expandIf(operation: InlineIfOperation, instance: Instance): Operation {
        val evaluator = compileTimeExpressionEvaluatorProvider.getEvaluator(instance)

        if (evaluator.evaluateBoolean(operation.guard)) {
            return operation.body.copy()
        }

        if (operation.`else` != null) {
            return operation.`else`.copy()
        }

        return OxstsFactory.createSequenceOperation()
    }

    private fun expandSeqFor(operation: InlineSeqFor, instance: Instance): Operation {
        val values = enumerateLoopRange(operation.rangeExpression, instance)
        val inlinedFor = OxstsFactory.createSequenceOperation()

        for (value in values) {
            val inlinedBody = operation.body.copy()
            expressionRewriter.rewriteReferencesTo(operation.loopVariable, inlinedBody, value)
            inlinedFor.steps += inlinedBody
        }

        if (inlinedFor.steps.isEmpty()) {
            return if (operation.`else` == null) {
                inlinedFor
            } else {
                operation.`else`.copy()
            }
        }

        if (inlinedFor.steps.size == 1) {
            return inlinedFor.steps.single()
        }

        return inlinedFor
    }

    private fun expandChoiceFor(operation: InlineChoiceFor, instance: Instance): Operation {
        val values = enumerateLoopRange(operation.rangeExpression, instance)
        val inlinedChoice = OxstsFactory.createChoiceOperation()

        for (value in values) {
            val inlinedBody = operation.body.copy()
            expressionRewriter.rewriteReferencesTo(operation.loopVariable, inlinedBody, value)
            inlinedChoice.branches += inlinedBody
        }

        if (inlinedChoice.branches.isEmpty()) {
            return if (operation.`else` == null) {
                OxstsFactory.createSequenceOperation()
            } else {
                operation.`else`.copy()
            }
        }

        if (inlinedChoice.branches.size == 1) {
            return inlinedChoice.branches.single()
        }

        return inlinedChoice
    }

    private fun enumerateLoopRange(rangeExpression: Expression, instance: Instance): List<Expression> {
        val evaluator = compileTimeExpressionEvaluatorProvider.getEvaluator(instance)
        val evaluation = evaluator.evaluate(rangeExpression)
        return when (evaluation) {
            is InstanceEvaluation -> evaluation.instances.map {
                instanceReferenceProvider.getReference(it)
            }
            is RangeEvaluation -> {
                if (evaluation.lowerBound == RangeEvaluation.INFINITY || evaluation.upperBound == RangeEvaluation.INFINITY) {
                    sourceError(rangeExpression, "inline for requires a bounded range; the range is unbounded")
                }
                (evaluation.lowerBound..evaluation.upperBound).map {
                    OxstsFactory.createLiteralInteger(it)
                }
            }
            else -> sourceError(
                rangeExpression,
                "inline for range must evaluate to instances or a bounded range; got ${evaluation::class.simpleName}",
            )
        }
    }

}
