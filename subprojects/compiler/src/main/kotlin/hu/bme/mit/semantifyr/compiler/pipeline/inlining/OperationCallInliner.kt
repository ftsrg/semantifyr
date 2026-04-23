/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import com.google.inject.Inject
import com.google.inject.assistedinject.Assisted
import com.google.inject.assistedinject.AssistedInject
import hu.bme.mit.semantifyr.oxsts.lang.utils.OperationVisitor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TraceOperation
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.optimizers.NestedOperationOptimizer
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
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
    private val compilationArtifactManager: CompilationArtifactManager,
    private val inlineOperationExpander: InlineOperationExpander,
) : OperationVisitor<Unit>() {

    private val logger by loggerFactory()

    private val expressionCallInliner = expressionCallInlinerFactory.create(instance)

    private var localVariableIndex: Int = 0

    private val processorQueue = ArrayDeque<Operation>()

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
            "OperationCallInliner.process: $elapsed total, $nestedOptimizeCalls nested-optimize call(s), $nestedOptimizeTotal inside"
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
        val inlined = inlineOperationExpander.expand(operation, instance) { localVariableIndex++ }
        EcoreUtil2.replace(operation, inlined)

        val actualInlined = simplifyInlinedOperation(inlined)
        for (index in actualInlined.indices.reversed()) {
            processorQueue.addFirst(actualInlined[index])
        }

        compilationArtifactManager.commitStep(CompilationPass.OperationCallInlining)
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

    interface Factory {
        fun create(instance: Instance): OperationCallInliner
    }

}
