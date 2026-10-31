/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import com.google.inject.assistedinject.Assisted
import com.google.inject.assistedinject.AssistedInject
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationStats
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.optimizers.NestedOperationOptimizer
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import org.eclipse.xtext.EcoreUtil2
import kotlin.time.TimeSource.Monotonic.markNow

class OperationCallInliner @AssistedInject constructor(
    @param:Assisted val instance: Instance,
    expressionCallInlinerFactory: ExpressionCallInliner.Factory,
    private val nestedOperationOptimizer: NestedOperationOptimizer,
    private val compilationArtifactManager: CompilationArtifactManager,
    private val inlineOperationExpander: InlineOperationExpander,
) : OperationVisitor<Unit>() {

    private val logger by loggerFactory()

    private val expressionCallInliner = expressionCallInlinerFactory.create(instance)
    private val processorQueue = ArrayDeque<Operation>()

    private var nestedOptimization = OptimizationStats()

    fun process(operation: Operation): OptimizationStats {
        nestedOptimization = OptimizationStats()

        val mark = markNow()
        visit(operation)
        processAll()
        val elapsed = mark.elapsedNow()

        logger.info {
            "OperationCallInliner.process: $elapsed total, ${nestedOptimization.passes.sumOf { it.runs }} nested-optimize call(s), ${nestedOptimization.duration} inside"
        }
        return nestedOptimization
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
        val expression = operation.expression
        if (expression != null) {
            expressionCallInliner.process(expression)
            return
        }
        val sequence = operation.eContainer() as SequenceOperation
        sequence.steps.add(
            sequence.steps.indexOf(operation) + 1,
            OxstsFactory.createHavocOperation().also {
                it.reference = OxstsFactory.createElementReference(operation)
            },
        )
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
        val inlined = inlineOperationExpander.expand(operation, instance)
        EcoreUtil2.replace(operation, inlined)

        val actualInlined = simplifyInlinedOperation(inlined)
        for (index in actualInlined.indices.reversed()) {
            processorQueue.addFirst(actualInlined[index])
        }

        compilationArtifactManager.commitStep(CompilationPass.OperationCallInlining)
    }

    private fun simplifyInlinedOperation(inlinedOperation: Operation): List<Operation> {
        nestedOptimization += nestedOperationOptimizer.optimize(inlinedOperation)

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
