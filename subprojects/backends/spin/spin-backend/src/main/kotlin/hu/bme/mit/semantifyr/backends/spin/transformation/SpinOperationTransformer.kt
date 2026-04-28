/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.transformation.HavocValueCollector
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class SpinOperationTransformer {
    @Inject
    private lateinit var spinExpressionTransformer: SpinExpressionTransformer

    @Inject
    private lateinit var spinVariableTransformer: SpinVariableTransformer

    @Inject
    private lateinit var havocValueCollector: HavocValueCollector

    fun transform(
        operation: Operation,
        indent: String = "    ",
    ): String {
        val builder = StringBuilder()
        emit(operation, builder, indent)
        return builder.toString()
    }

    private fun emit(
        operation: Operation,
        out: StringBuilder,
        indent: String,
    ) {
        when (operation) {
            is SequenceOperation -> for (step in operation.steps) emit(step, out, indent)
            is ChoiceOperation -> emitChoice(operation, out, indent)
            is AssignmentOperation -> emitAssignment(operation, out, indent)
            is AssumptionOperation -> emitAssumption(operation, out, indent)
            is IfOperation -> emitIf(operation, out, indent)
            is LocalVarDeclarationOperation -> emitLocalVarDecl(operation, out, indent)
            is HavocOperation -> emitHavoc(operation, out, indent)
            else -> error("Operation of kind ${operation::class.simpleName} is not supported by the Spin backend")
        }
    }

    private fun emitChoice(
        operation: ChoiceOperation,
        out: StringBuilder,
        indent: String,
    ) {
        out.append(indent).append("if\n")
        for (branch in operation.branches) {
            out.append(indent).append(":: ")
            if (branch.steps.isEmpty()) {
                out.append("skip\n")
            } else {
                out.append("atomic {\n")
                emit(branch, out, "$indent    ")
                out.append(indent).append("}\n")
            }
        }
        out.append(indent).append("fi;\n")
    }

    private fun emitAssignment(
        operation: AssignmentOperation,
        out: StringBuilder,
        indent: String,
    ) {
        val variable = resolveVariable(operation.reference)
        val name = spinVariableTransformer.nameOf(variable)
        val rhs = spinExpressionTransformer.transform(operation.expression)
        out
            .append(indent)
            .append(name)
            .append(" = ")
            .append(rhs)
            .append(";\n")
    }

    private fun emitAssumption(
        operation: AssumptionOperation,
        out: StringBuilder,
        indent: String,
    ) {
        val guard = spinExpressionTransformer.transform(operation.expression)
        // A bare expression statement in Promela blocks until true — that's exactly `assume`.
        out
            .append(indent)
            .append("(")
            .append(guard)
            .append(");\n")
    }

    private fun emitIf(
        operation: IfOperation,
        out: StringBuilder,
        indent: String,
    ) {
        val guard = spinExpressionTransformer.transform(operation.guard)
        out.append(indent).append("if\n")
        out
            .append(indent)
            .append(":: (")
            .append(guard)
            .append(") ->\n")
        emit(operation.body, out, "$indent    ")
        val elseBranch = operation.`else`
        if (elseBranch != null) {
            out.append(indent).append(":: else ->\n")
            emit(elseBranch, out, "$indent    ")
        } else {
            out.append(indent).append(":: else -> skip\n")
        }
        out.append(indent).append("fi;\n")
    }

    private fun emitLocalVarDecl(
        operation: LocalVarDeclarationOperation,
        out: StringBuilder,
        indent: String,
    ) {
        val name = spinVariableTransformer.nameOf(operation)
        val type = SpinTypeRenderer.renderLocalType(operation, spinVariableTransformer)
        val init = operation.expression?.let { " = ${spinExpressionTransformer.transform(it)}" } ?: ""
        out
            .append(indent)
            .append(type)
            .append(' ')
            .append(name)
            .append(init)
            .append(";\n")
    }

    private fun emitHavoc(
        operation: HavocOperation,
        out: StringBuilder,
        indent: String,
    ) {
        val variable = resolveVariable(operation.reference)
        val described = spinVariableTransformer.describe(variable)
        val name = described.name
        when (described.kind) {
            SpinVariableKind.Boolean -> {
                out.append(indent).append("if\n")
                out.append(indent).append(":: ").append(name).append(" = false\n")
                out.append(indent).append(":: ").append(name).append(" = true\n")
                out.append(indent).append("fi;\n")
            }
            SpinVariableKind.Enum -> {
                val enum = described.enumDeclaration
                    ?: error("Enum-typed havoc target '$name' has no enum declaration")
                out.append(indent).append("if\n")
                for (literal in enum.literals) {
                    val literalName = spinVariableTransformer.sanitizeEnumLiteral(literal)
                    out.append(indent).append(":: ").append(name).append(" = ").append(literalName).append("\n")
                }
                out.append(indent).append("fi;\n")
            }
            SpinVariableKind.Integer -> {
                // Enumerate only the integer values the model actually distinguishes (constants and
                // operands of comparisons against this variable). A blanket `select(v : INT_MIN..INT_MAX)`
                // would force Spin's DFS to walk 2^32 values, which always trips the depth bound.
                val values = havocValueCollector.valuesFor(variable)
                out.append(indent).append("if\n")
                for (value in values) {
                    out.append(indent).append(":: ").append(name).append(" = ").append(value).append("\n")
                }
                out.append(indent).append("fi;\n")
            }
        }
    }

    private fun resolveVariable(expression: org.eclipse.emf.ecore.EObject): VariableDeclaration {
        val ref = expression as? ElementReference
            ?: error("Unsupported reference shape on the left-hand side of an assignment: ${expression::class.simpleName}")
        val element = ref.element
        return element as? VariableDeclaration
            ?: error("Expected a variable reference on the left-hand side of an assignment, got ${element::class.simpleName}")
    }
}

internal object SpinTypeRenderer {
    fun renderLocalType(
        variable: VariableDeclaration,
        transformer: SpinVariableTransformer,
    ): String {
        val described = transformer.describe(variable)
        return when (described.kind) {
            SpinVariableKind.Integer -> "int"
            SpinVariableKind.Boolean -> "bool"
            // For a local enum-typed var we fall back to int; the global emits the proper mtype.
            SpinVariableKind.Enum -> "int"
        }
    }
}
