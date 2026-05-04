/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.transformation.HavocValueCollector
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalEdge
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalLocation
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalLocationKind
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation

class UppaalOperationTransformer {
    @Inject
    private lateinit var uppaalExpressionTransformer: UppaalExpressionTransformer

    @Inject
    private lateinit var uppaalVariableTransformer: UppaalVariableTransformer

    @Inject
    private lateinit var havocValueCollector: HavocValueCollector

    class EmissionContext {
        val locations: MutableList<UppaalLocation> = mutableListOf()
        val edges: MutableList<UppaalEdge> = mutableListOf()
        private var nextId: Int = 0

        fun freshCommitted(name: String = "c"): UppaalLocation {
            val id = "loc_$nextId"
            nextId++
            val location = UppaalLocation(
                id = id,
                name = "${name}_$id",
                kind = UppaalLocationKind.Committed,
            )
            locations += location
            return location
        }
    }

    fun transform(
        ctx: EmissionContext,
        sourceId: String,
        targetId: String,
        operation: Operation,
    ) {
        when (operation) {
            is SequenceOperation -> {
                transformSequence(ctx, sourceId, targetId, operation)
            }
            is ChoiceOperation -> {
                transformChoice(ctx, sourceId, targetId, operation)
            }
            is AssignmentOperation -> {
                transformAssignment(ctx, sourceId, targetId, operation)
            }
            is AssumptionOperation -> {
                transformAssumption(ctx, sourceId, targetId, operation)
            }
            is IfOperation -> {
                transformIf(ctx, sourceId, targetId, operation)
            }
            is LocalVarDeclarationOperation -> {
                transformLocalVarDecl(ctx, sourceId, targetId, operation)
            }
            is HavocOperation -> {
                transformHavoc(ctx, sourceId, targetId, operation)
            }
            else -> {
                error("Operation of kind ${operation::class.simpleName} is not supported by the Uppaal backend")
            }
        }
    }

    private fun transformSequence(
        ctx: EmissionContext,
        sourceId: String,
        targetId: String,
        operation: SequenceOperation,
    ) {
        val steps = operation.steps
        if (steps.isEmpty()) {
            ctx.edges += UppaalEdge(sourceId = sourceId, targetId = targetId)
            return
        }
        var currentSource = sourceId
        for ((index, step) in steps.withIndex()) {
            val isLast = index == steps.size - 1
            val nextTarget = if (isLast) {
                targetId
            } else {
                ctx.freshCommitted("seq").id
            }
            transform(ctx, currentSource, nextTarget, step)
            currentSource = nextTarget
        }
    }

    private fun transformChoice(
        ctx: EmissionContext,
        sourceId: String,
        targetId: String,
        operation: ChoiceOperation,
    ) {
        val branches = operation.branches
        if (branches.isEmpty()) {
            // An empty choice would block forever. Model it with a
            // `false` guard so the Uppaal model stays well-formed;
            // the edge is provably unreachable.
            ctx.edges += UppaalEdge(sourceId = sourceId, targetId = targetId, guard = "false")
            return
        }
        for (branch in branches) {
            transform(ctx, sourceId, targetId, branch)
        }
    }

    private fun transformAssignment(
        ctx: EmissionContext,
        sourceId: String,
        targetId: String,
        operation: AssignmentOperation,
    ) {
        val variable = OxstsUtils.requireVariableReference(operation.reference)
        val name = uppaalVariableTransformer.nameOf(variable)
        val isClock = uppaalVariableTransformer.isClock(variable)

        val assignment = if (isClock) {
            val literal = operation.expression as? LiteralInteger
                ?: error("Clock variable '$name' can only be reset to 0 (got a non-literal expression)")
            require(literal.value == 0) {
                "Clock variable '$name' can only be reset to 0 (got ${literal.value})"
            }
            "$name = 0"
        } else {
            "$name = ${uppaalExpressionTransformer.transform(operation.expression)}"
        }
        ctx.edges += UppaalEdge(
            sourceId = sourceId,
            targetId = targetId,
            assignment = assignment,
        )
    }

    private fun transformAssumption(
        ctx: EmissionContext,
        sourceId: String,
        targetId: String,
        operation: AssumptionOperation,
    ) {
        val guard = uppaalExpressionTransformer.transform(operation.expression)
        ctx.edges += UppaalEdge(
            sourceId = sourceId,
            targetId = targetId,
            guard = guard,
        )
    }

    private fun transformIf(
        ctx: EmissionContext,
        sourceId: String,
        targetId: String,
        operation: IfOperation,
    ) {
        val guard = uppaalExpressionTransformer.transform(operation.guard)
        val negated = "!($guard)"

        val thenEntry = ctx.freshCommitted("then")
        ctx.edges += UppaalEdge(
            sourceId = sourceId,
            targetId = thenEntry.id,
            guard = guard,
        )
        transform(ctx, thenEntry.id, targetId, operation.body)

        val elseEntry = ctx.freshCommitted("else")
        ctx.edges += UppaalEdge(
            sourceId = sourceId,
            targetId = elseEntry.id,
            guard = negated,
        )
        val elseBranch = operation.`else`
        if (elseBranch != null) {
            transform(ctx, elseEntry.id, targetId, elseBranch)
        } else {
            ctx.edges += UppaalEdge(sourceId = elseEntry.id, targetId = targetId)
        }
    }

    private fun transformHavoc(
        ctx: EmissionContext,
        sourceId: String,
        targetId: String,
        operation: HavocOperation,
    ) {
        val variable = OxstsUtils.requireVariableReference(operation.reference)
        val name = uppaalVariableTransformer.nameOf(variable)
        if (uppaalVariableTransformer.isClock(variable)) {
            error("Uppaal backend does not support havoc on clock variable '$name' (clocks may only be reset to 0).")
        }
        val described = uppaalVariableTransformer.describe(variable)
        when (described.kind) {
            UppaalVariableKind.Integer -> {
                // Uppaal's default int domain is [-32768, 32767]; a `select : int` would force the
                // engine to enumerate all 65k values per havoc. Emit one parallel edge per
                // "interesting" value the model actually distinguishes (via comparisons), same
                // strategy Gamma uses for Spin/Promela.
                val values = havocValueCollector.valuesFor(variable)
                for (value in values) {
                    ctx.edges += UppaalEdge(
                        sourceId = sourceId,
                        targetId = targetId,
                        assignment = "$name = $value",
                    )
                }
            }
            UppaalVariableKind.Boolean -> {
                val selectName = "havoc_${name}_${nextHavocId++}"
                ctx.edges += UppaalEdge(
                    sourceId = sourceId,
                    targetId = targetId,
                    select = "$selectName : int[0, 1]",
                    assignment = "$name = $selectName",
                )
            }
            UppaalVariableKind.Enum -> {
                val enum = described.enumDeclaration
                    ?: error("Enum-typed havoc target '$name' has no enum declaration")
                val selectName = "havoc_${name}_${nextHavocId++}"
                ctx.edges += UppaalEdge(
                    sourceId = sourceId,
                    targetId = targetId,
                    select = "$selectName : int[0, ${enum.literals.size - 1}]",
                    assignment = "$name = $selectName",
                )
            }
            UppaalVariableKind.Clock -> error("unreachable: clock handled above")
        }
    }

    private var nextHavocId: Int = 0

    private fun transformLocalVarDecl(
        ctx: EmissionContext,
        sourceId: String,
        targetId: String,
        operation: LocalVarDeclarationOperation,
    ) {
        val initExpr = operation.expression
        if (initExpr == null) {
            // Declaration-only: the variable is global-hoisted by
            // UppaalModelGenerator, nothing to emit on the edge.
            ctx.edges += UppaalEdge(sourceId = sourceId, targetId = targetId)
            return
        }
        val name = uppaalVariableTransformer.nameOf(operation)
        val assignment = "$name = ${uppaalExpressionTransformer.transform(initExpr)}"
        ctx.edges += UppaalEdge(
            sourceId = sourceId,
            targetId = targetId,
            assignment = assignment,
        )
    }

}
