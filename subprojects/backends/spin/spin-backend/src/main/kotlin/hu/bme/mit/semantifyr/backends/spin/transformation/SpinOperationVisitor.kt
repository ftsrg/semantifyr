/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.transformation

import com.google.inject.assistedinject.Assisted
import com.google.inject.assistedinject.AssistedInject
import hu.bme.mit.semantifyr.backend.BackendUnsupportedException
import hu.bme.mit.semantifyr.backend.text.IndentingBuilder
import hu.bme.mit.semantifyr.backend.transformation.BackendOperationVisitor
import hu.bme.mit.semantifyr.backend.transformation.HavocValueCollector
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class SpinOperationVisitor @AssistedInject constructor(
    @param:Assisted private val builder: IndentingBuilder,
    private val spinExpressionTransformer: SpinExpressionTransformer,
    private val spinVariableTransformer: SpinVariableTransformer,
    private val havocValueCollector: HavocValueCollector,
) : BackendOperationVisitor<Unit>() {

    fun transform(operation: Operation) {
        visit(operation)
    }

    override fun visit(operation: SequenceOperation) {
        for (step in operation.steps) {
            visit(step)
        }
    }

    override fun visit(operation: ChoiceOperation) {
        builder.line("if")
        for (branch in operation.branches) {
            renderChoiceBranch(branch)
        }
        builder.line("fi;")
    }

    private fun renderChoiceBranch(branch: SequenceOperation) {
        if (branch.steps.isEmpty()) {
            builder.line(":: skip")
            return
        }
        builder.line(":: atomic {")
        builder.indented {
            visit(branch)
        }
        builder.line("}")
    }

    override fun visit(operation: AssignmentOperation) {
        val variable = OxstsUtils.requireVariableReference(operation.reference)
        val name = spinVariableTransformer.nameOf(variable)
        val rightHandSide = spinExpressionTransformer.transform(operation.expression)
        builder.line("$name = $rightHandSide;")
    }

    override fun visit(operation: AssumptionOperation) {
        val guard = spinExpressionTransformer.transform(operation.expression)
        builder.line("($guard);")
    }

    override fun visit(operation: IfOperation) {
        val guard = spinExpressionTransformer.transform(operation.guard)
        builder.line("if")
        builder.line(":: ($guard) ->")
        builder.indented {
            visit(operation.body)
        }
        renderIfElseBranch(operation.`else`)
        builder.line("fi;")
    }

    private fun renderIfElseBranch(elseBranch: Operation?) {
        if (elseBranch == null) {
            builder.line(":: else -> skip")
            return
        }
        builder.line(":: else ->")
        builder.indented {
            visit(elseBranch)
        }
    }

    override fun visit(operation: LocalVarDeclarationOperation) {
        val name = spinVariableTransformer.nameOf(operation)
        val type = renderLocalType(operation)
        val initializer = operation.expression?.let {
            " = ${spinExpressionTransformer.transform(it)}"
        } ?: ""
        builder.line("$type $name$initializer;")
    }

    private fun renderLocalType(operation: LocalVarDeclarationOperation): String {
        return when (spinVariableTransformer.describe(operation).kind) {
            SpinVariableKind.Integer -> "int"
            SpinVariableKind.Boolean -> "bool"
            // For a local enum-typed var we fall back to int; the global emits the proper mtype.
            SpinVariableKind.Enum -> "int"
        }
    }

    override fun visit(operation: HavocOperation) {
        val variable = OxstsUtils.requireVariableReference(operation.reference)
        val described = spinVariableTransformer.describe(variable)
        when (described.kind) {
            SpinVariableKind.Boolean -> renderBooleanHavoc(described.name)
            SpinVariableKind.Enum -> renderEnumHavoc(described)
            SpinVariableKind.Integer -> renderIntegerHavoc(variable, described.name)
        }
    }

    private fun renderBooleanHavoc(name: String) {
        builder.line("if")
        builder.line(":: $name = false")
        builder.line(":: $name = true")
        builder.line("fi;")
    }

    private fun renderEnumHavoc(variable: SpinVariable) {
        val enum = variable.enumDeclaration
            ?: error("Enum-typed havoc target '${variable.name}' has no enum declaration")
        builder.line("if")
        for (literal in enum.literals) {
            val literalName = spinVariableTransformer.sanitizeEnumLiteral(literal)
            builder.line(":: ${variable.name} = $literalName")
        }
        builder.line("fi;")
    }

    private fun renderIntegerHavoc(variable: VariableDeclaration, name: String) {
        val values = havocValueCollector.valuesFor(variable)
        builder.line("if")
        for (value in values) {
            builder.line(":: $name = $value")
        }
        builder.line("fi;")
    }

    override fun visit(operation: ForOperation) {
        throw BackendUnsupportedException("Spin does not support for-operations")
    }

    interface Factory {
        fun create(builder: IndentingBuilder): SpinOperationVisitor
    }
}
