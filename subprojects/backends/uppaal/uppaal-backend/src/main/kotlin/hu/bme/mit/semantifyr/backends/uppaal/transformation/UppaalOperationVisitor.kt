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
    @Assisted("sourceId") private var sourceId: String,
    @Assisted("targetId") private var targetId: String,
    private val uppaalExpressionTransformer: UppaalExpressionTransformer,
    private val uppaalGuardTransformer: UppaalGuardTransformer,
    private val uppaalVariableTransformer: UppaalVariableTransformer,
    private val havocValueCollector: HavocValueCollector,
) : BackendOperationVisitor<Unit>() {

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
        if (uppaalVariableTransformer.kindOf(variable) == UppaalVariableKind.Clock) {
            renderClockReset(variable, name, operation)
            return
        }
        val assignment = "$name = ${uppaalExpressionTransformer.transform(operation.expression)}"
        context.edges += UppaalEdge(sourceId, targetId, assignment = assignment)
    }

    private fun renderClockReset(
        variable: VariableDeclaration,
        name: String,
        operation: AssignmentOperation,
    ) {
        val literal = operation.expression as? LiteralInteger
        if (literal?.value != 0) {
            throw BackendUnsupportedException("Uppaal resets clock $name only to 0")
        }
        val location = context.freshClockLocation(UppaalClockEdge.Reset(variable))
        context.edges += UppaalEdge(sourceId, location.id, assignment = "$name = 0")
        context.edges += UppaalEdge(location.id, targetId)
    }

    override fun visit(operation: AssumptionOperation) {
        renderGuarded(sourceId, targetId, uppaalGuardTransformer.disjunctsOf(operation.expression))
    }

    private fun renderGuarded(
        source: String,
        target: String,
        guards: List<UppaalGuard>,
    ) {
        for (guard in guards) {
            val rendered = uppaalGuardTransformer.render(guard)
            if (guard.clockAtoms.isEmpty()) {
                context.edges += UppaalEdge(source, target, guard = rendered)
            } else {
                val location = context.freshClockLocation(UppaalClockEdge.Test(guard.clockAtoms))
                context.edges += UppaalEdge(source, location.id, guard = rendered)
                context.edges += UppaalEdge(location.id, target)
            }
        }
    }

    override fun visit(operation: IfOperation) {
        val guards = uppaalGuardTransformer.disjunctsOf(operation.guard)
        val negated = uppaalGuardTransformer.disjunctsOfNegated(operation.guard)
        val savedSource = sourceId
        val savedTarget = targetId

        renderIfBranch(savedSource, savedTarget, guards, operation.body)
        renderElseBranch(savedSource, savedTarget, negated, operation.`else`)

        sourceId = savedSource
        targetId = savedTarget
    }

    private fun renderIfBranch(
        savedSource: String,
        savedTarget: String,
        guards: List<UppaalGuard>,
        body: Operation,
    ) {
        val thenEntry = context.freshCommitted("then")
        renderGuarded(savedSource, thenEntry.id, guards)
        sourceId = thenEntry.id
        targetId = savedTarget
        visit(body)
    }

    private fun renderElseBranch(
        savedSource: String,
        savedTarget: String,
        negatedGuards: List<UppaalGuard>,
        elseBranch: Operation?,
    ) {
        val elseEntry = context.freshCommitted("else")
        renderGuarded(savedSource, elseEntry.id, negatedGuards)
        if (elseBranch == null) {
            context.edges += UppaalEdge(elseEntry.id, savedTarget)
            return
        }
        sourceId = elseEntry.id
        targetId = savedTarget
        visit(elseBranch)
    }

    override fun visit(operation: LocalVarDeclarationOperation) {
        val initExpression = operation.expression ?: error("Local variable ${operation.name} has no initializer")
        val name = uppaalVariableTransformer.nameOf(operation)
        val assignment = "$name = ${uppaalExpressionTransformer.transform(initExpression)}"
        context.edges += UppaalEdge(sourceId, targetId, assignment = assignment)
    }

    override fun visit(operation: HavocOperation) {
        havoc(OxstsUtils.requireVariableReference(operation.reference))
    }

    private fun havoc(variable: VariableDeclaration) {
        val name = uppaalVariableTransformer.nameOf(variable)
        val described = uppaalVariableTransformer.describe(variable)
        when (described.kind) {
            UppaalVariableKind.Integer -> renderIntegerHavoc(variable, name)
            UppaalVariableKind.Boolean -> renderBooleanHavoc(name)
            UppaalVariableKind.Enum -> renderEnumHavoc(described, name)
            UppaalVariableKind.Clock -> throw BackendUnsupportedException("Uppaal does not support havoc on clock $name")
        }
    }

    private fun renderIntegerHavoc(variable: VariableDeclaration, name: String) {
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
