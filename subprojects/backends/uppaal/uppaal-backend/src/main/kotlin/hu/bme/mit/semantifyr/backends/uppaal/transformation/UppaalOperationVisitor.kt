/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import com.google.inject.assistedinject.Assisted
import com.google.inject.assistedinject.AssistedInject
import hu.bme.mit.semantifyr.backend.BackendUnsupportedException
import hu.bme.mit.semantifyr.backend.transformation.BackendOperationVisitor
import hu.bme.mit.semantifyr.backend.transformation.HavocValueCollector
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalEdge
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class UppaalOperationVisitor @AssistedInject constructor(
    @param:Assisted private val context: UppaalEmissionContext,
    @Assisted("sourceId") sourceId: String,
    @Assisted("targetId") targetId: String,
    private val uppaalExpressionTransformer: UppaalExpressionTransformer,
    private val uppaalVariableTransformer: UppaalVariableTransformer,
    private val havocValueCollector: HavocValueCollector,
) : BackendOperationVisitor<Unit>() {

    private var sourceId: String = sourceId
    private var targetId: String = targetId

    fun transform(operation: Operation) {
        visit(operation)
    }

    override fun visit(operation: SequenceOperation) {
        val steps = operation.steps
        if (steps.isEmpty()) {
            context.edges += UppaalEdge(sourceId, targetId)
            return
        }
        val finalTarget = targetId
        val initialSource = sourceId
        var currentSource = initialSource
        for ((index, step) in steps.withIndex()) {
            val isLast = index == steps.size - 1
            val nextTarget = if (isLast) {
                finalTarget
            } else {
                context.freshCommitted("seq").id
            }
            sourceId = currentSource
            targetId = nextTarget
            visit(step)
            currentSource = nextTarget
        }
        sourceId = initialSource
        targetId = finalTarget
    }

    override fun visit(operation: ChoiceOperation) {
        val branches = operation.branches
        if (branches.isEmpty()) {
            // An empty choice would block forever. Model it with a `false` guard so the Uppaal
            // model stays well-formed; the edge is provably unreachable.
            context.edges += UppaalEdge(sourceId, targetId, guard = "false")
            return
        }
        val savedSource = sourceId
        val savedTarget = targetId
        for (branch in branches) {
            sourceId = savedSource
            targetId = savedTarget
            visit(branch)
        }
        sourceId = savedSource
        targetId = savedTarget
    }

    override fun visit(operation: AssignmentOperation) {
        val variable = OxstsUtils.requireVariableReference(operation.reference)
        val name = uppaalVariableTransformer.nameOf(variable)
        val assignment = if (uppaalVariableTransformer.isClock(variable)) {
            renderClockReset(name, operation)
        } else {
            "$name = ${uppaalExpressionTransformer.transform(operation.expression)}"
        }
        context.edges += UppaalEdge(sourceId, targetId, assignment = assignment)
    }

    private fun renderClockReset(name: String, operation: AssignmentOperation): String {
        val literal = operation.expression as? LiteralInteger
            ?: error("Clock variable '$name' can only be reset to 0 (got a non-literal expression)")
        require(literal.value == 0) {
            "Clock variable '$name' can only be reset to 0 (got ${literal.value})"
        }
        return "$name = 0"
    }

    override fun visit(operation: AssumptionOperation) {
        val guard = uppaalExpressionTransformer.transform(operation.expression)
        context.edges += UppaalEdge(sourceId, targetId, guard = guard)
    }

    override fun visit(operation: IfOperation) {
        val guard = uppaalExpressionTransformer.transform(operation.guard)
        val negated = "!($guard)"
        val savedSource = sourceId
        val savedTarget = targetId

        renderIfBranch(savedSource, savedTarget, guard, operation.body)
        renderElseBranch(savedSource, savedTarget, negated, operation.`else`)

        sourceId = savedSource
        targetId = savedTarget
    }

    private fun renderIfBranch(
        savedSource: String,
        savedTarget: String,
        guard: String,
        body: Operation,
    ) {
        val thenEntry = context.freshCommitted("then")
        context.edges += UppaalEdge(savedSource, thenEntry.id, guard = guard)
        sourceId = thenEntry.id
        targetId = savedTarget
        visit(body)
    }

    private fun renderElseBranch(
        savedSource: String,
        savedTarget: String,
        negatedGuard: String,
        elseBranch: Operation?,
    ) {
        val elseEntry = context.freshCommitted("else")
        context.edges += UppaalEdge(savedSource, elseEntry.id, guard = negatedGuard)
        if (elseBranch == null) {
            context.edges += UppaalEdge(elseEntry.id, savedTarget)
            return
        }
        sourceId = elseEntry.id
        targetId = savedTarget
        visit(elseBranch)
    }

    override fun visit(operation: LocalVarDeclarationOperation) {
        val initExpression = operation.expression
        if (initExpression == null) {
            // Declaration-only: the variable is global-hoisted by the model transformer; nothing
            // to emit on the edge.
            context.edges += UppaalEdge(sourceId, targetId)
            return
        }
        val name = uppaalVariableTransformer.nameOf(operation)
        val assignment = "$name = ${uppaalExpressionTransformer.transform(initExpression)}"
        context.edges += UppaalEdge(sourceId, targetId, assignment = assignment)
    }

    override fun visit(operation: HavocOperation) {
        val variable = OxstsUtils.requireVariableReference(operation.reference)
        val name = uppaalVariableTransformer.nameOf(variable)
        if (uppaalVariableTransformer.isClock(variable)) {
            error("Uppaal backend does not support havoc on clock variable '$name' (clocks may only be reset to 0).")
        }
        val described = uppaalVariableTransformer.describe(variable)
        when (described.kind) {
            UppaalVariableKind.Integer -> renderIntegerHavoc(variable, name)
            UppaalVariableKind.Boolean -> renderBooleanHavoc(name)
            UppaalVariableKind.Enum -> renderEnumHavoc(described, name)
            UppaalVariableKind.Clock -> error("unreachable: clock handled above")
        }
    }

    private fun renderIntegerHavoc(variable: VariableDeclaration, name: String) {
        // Uppaal's default int domain is [-32768, 32767]; a select : int would force the engine
        // to enumerate all 65k values per havoc. Emit one parallel edge per value the model
        // actually distinguishes via comparisons, same strategy Gamma uses for Spin/Promela.
        val values = havocValueCollector.valuesFor(variable)
        for (value in values) {
            context.edges += UppaalEdge(sourceId, targetId, assignment = "$name = $value")
        }
    }

    private fun renderBooleanHavoc(name: String) {
        val selectName = context.freshHavocSelectName(name)
        context.edges += UppaalEdge(
            sourceId,
            targetId,
            select = "$selectName : int[0, 1]",
            assignment = "$name = $selectName",
        )
    }

    private fun renderEnumHavoc(variable: UppaalVariable, name: String) {
        val enum = variable.enumDeclaration
            ?: error("Enum-typed havoc target '$name' has no enum declaration")
        val selectName = context.freshHavocSelectName(name)
        context.edges += UppaalEdge(
            sourceId,
            targetId,
            select = "$selectName : int[0, ${enum.literals.size - 1}]",
            assignment = "$name = $selectName",
        )
    }

    override fun visit(operation: ForOperation) {
        throw BackendUnsupportedException("Uppaal does not support for-operations")
    }

    interface Factory {
        fun create(
            context: UppaalEmissionContext,
            @Assisted("sourceId") sourceId: String,
            @Assisted("targetId") targetId: String,
        ): UppaalOperationVisitor
    }
}
