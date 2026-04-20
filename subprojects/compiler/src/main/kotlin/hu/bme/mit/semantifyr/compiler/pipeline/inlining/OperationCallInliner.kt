/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import com.google.inject.Inject
import com.google.inject.assistedinject.Assisted
import com.google.inject.assistedinject.AssistedInject
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.RangeEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.utils.OperationVisitor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineCall
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineChoiceFor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineIfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineSeqFor
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TraceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceEvaluation
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceReferenceProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.RedefinitionAwareReferenceResolver
import hu.bme.mit.semantifyr.compiler.pipeline.expression.StaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.evaluateTyped
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.optimizers.NestedOperationOptimizer
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.TransitionCallTraceBuilder
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.pipeline.utils.sourceError
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import org.eclipse.xtext.EcoreUtil2
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.TimeSource.Monotonic.markNow

class OperationCallInliner @AssistedInject @Inject constructor(
    @param:Assisted val instance: Instance,
    expressionCallInlinerFactory: ExpressionCallInliner.Factory,
    private val nestedOperationOptimizer: NestedOperationOptimizer,
    private val staticExpressionEvaluatorProvider: StaticExpressionEvaluatorProvider,
    private val metaStaticExpressionEvaluatorProvider: MetaStaticExpressionEvaluatorProvider,
    private val expressionRewriter: ExpressionRewriter,
    private val compilationArtifactManager: CompilationArtifactManager,
    private val instanceReferenceProvider: InstanceReferenceProvider,
    private val redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver,
    private val builtinAnnotationHandler: BuiltinAnnotationHandler,
    private val transitionCallTracer: TransitionCallTraceBuilder,
) : OperationVisitor<Unit>() {

    private val logger by loggerFactory()

    private val expressionCallInliner = expressionCallInlinerFactory.create(instance)

    private var localVariableIndex: Int = 0

    private val processorQueue = ArrayDeque<Operation>()

    // Aggregate stats for the nested-operation optimizer. The inliner calls it
    // once per inlined call expansion; per-call timing lives at debug on the
    // WorklistOptimizer. This is the info-level summary for the whole `process`
    // invocation - usually the dominant cost in compilation.
    private var nestedOptimizeCalls: Int = 0
    private var nestedOptimizeTotal: Duration = ZERO

    fun process(operation: Operation) {
        nestedOptimizeCalls = 0
        nestedOptimizeTotal = ZERO

        val mark = markNow()
        visit(operation)
        processAll()
        val elapsed = mark.elapsedNow()

        logger.info {
            "OperationCallInliner.process: ${elapsed} total, " +
                "${nestedOptimizeCalls} nested-optimize call(s), ${nestedOptimizeTotal} inside"
        }
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
        for (index in operation.branches.indices.reversed()) {
            processorQueue.addFirst(operation.branches[index])
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

    override fun visit(operation: AssignmentOperation) {
        expressionCallInliner.process(operation.expression)
    }

    override fun visit(operation: TraceOperation) {
        // NO-OP
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
        val inlined = createInlinedOperation(operation)
        EcoreUtil2.replace(operation, inlined)

        val actualInlined = simplifyInlinedOperation(inlined)
        for (index in actualInlined.indices.reversed()) {
            processorQueue.addFirst(actualInlined[index])
        }

        compilationArtifactManager.commitStep(CompilationPass.OperationCallInlining)
    }

    private fun rewriteLocalVariables(operation: Operation) {
        val localVars = operation.eAllOfType<LocalVarDeclarationOperation>().toList()

        for (localVar in localVars) {
            localVar.name = LocalVarNames.inlinedName(localVar, localVariableIndex++)
        }
    }

    private fun simplifyInlinedOperation(inlinedOperation: Operation): List<Operation> {
        val mark = markNow()
        nestedOperationOptimizer.optimize(inlinedOperation)
        nestedOptimizeTotal += mark.elapsedNow()
        nestedOptimizeCalls++

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

    private fun createInlinedOperation(operation: InlineOperation): Operation {
        return when (operation) {
            is InlineCall -> createInlinedOperation(operation)
            is InlineIfOperation -> createInlinedOperation(operation)
            is InlineSeqFor -> createInlinedOperation(operation)
            is InlineChoiceFor -> createInlinedOperation(operation)
            else -> error("Operation is not of known type: $operation")
        }
    }

    private fun createInlinedOperation(operation: InlineCall): Operation {
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
        val containerInstance = evaluator.evaluateSingleInstanceOrNull(containerInstanceReference)

        if (containerInstance == null) {
            if (transitionReferenceExpression is NavigationSuffixExpression && transitionReferenceExpression.isOptional) {
                return OxstsFactory.createSequenceOperation();
            }
            error("Transition holder is not an instance.")
        }

        return inlineTransitionCall(containerInstance, transitionDeclaration, callExpression)
    }

    private fun inlineTransitionCall(containerInstance: Instance, transitionDeclaration: TransitionDeclaration, callExpression: CallSuffixExpression): Operation {
        val containerInstanceReference = instanceReferenceProvider.getReference(containerInstance)

        val actualTransition = redefinitionAwareReferenceResolver.resolve(containerInstance.domain, transitionDeclaration) as TransitionDeclaration

        if (actualTransition.isAbstract) {
            error("Abstract transition can not be inlined!")
        }

        val inlined = OxstsFactory.createChoiceOperation().also {
            for (currentOperation in actualTransition.branches) {
                val inlinedOperation = currentOperation.copy()

                expressionRewriter.rewriteExpressionsToContext(inlinedOperation, containerInstanceReference)
                expressionRewriter.rewriteExpressionsToCall(inlinedOperation, actualTransition, callExpression)

                it.branches += inlinedOperation
            }
        }

        rewriteLocalVariables(inlined)

        val tracerOperation = if (builtinAnnotationHandler.isTransitionTraced(actualTransition)) {
            // trace this transition call with the static parameters
            //  name of the transition, name-value pairs of the static call argument expressions, including self
            //  somehow differentiate between transitions with the same name, i.e., recursive calls
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

    private fun createInlinedOperation(operation: InlineIfOperation): Operation {
        val evaluator = staticExpressionEvaluatorProvider.getEvaluator(instance)

        if (evaluator.evaluateBoolean(operation.guard)) {
            return operation.body.copy()
        }

        if (operation.`else` != null) {
            return operation.`else`.copy()
        }

        return OxstsFactory.createSequenceOperation()
    }

    private fun createInlinedOperation(operation: InlineSeqFor): Operation {
        val values = enumerateLoopRange(operation.rangeExpression)

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

    private fun createInlinedOperation(operation: InlineChoiceFor): Operation {
        val values = enumerateLoopRange(operation.rangeExpression)

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

    private fun enumerateLoopRange(rangeExpression: Expression): List<Expression> {
        val evaluator = staticExpressionEvaluatorProvider.getEvaluator(instance)
        val evaluation = evaluator.evaluate(rangeExpression)
        return when (evaluation) {
            is InstanceEvaluation -> evaluation.instances.map { instanceReferenceProvider.getReference(it) }
            is RangeEvaluation -> {
                if (evaluation.lowerBound == RangeEvaluation.INFINITY || evaluation.upperBound == RangeEvaluation.INFINITY) {
                    sourceError(rangeExpression, "inline for requires a bounded range; the range is unbounded")
                }
                (evaluation.lowerBound..evaluation.upperBound).map { OxstsFactory.createLiteralInteger(it) }
            }
            else -> sourceError(
                rangeExpression,
                "inline for range must evaluate to instances or a bounded range; got ${evaluation::class.simpleName}",
            )
        }
    }

    interface Factory {
        fun create(instance: Instance): OperationCallInliner
    }

}
