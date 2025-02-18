/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.serialization

import hu.bme.mit.semantifyr.oxsts.model.oxsts.AndOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DeclarationReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Enum
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EqualityOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.GreaterThanOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.GreaterThanOrEqualsOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InequalityOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IntegerType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LessThanOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LessThanOrEqualsOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.MinusOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NotOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OperatorExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OrOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PlusOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Property
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ReferenceTyping
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Transition
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Typing
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Variable
import hu.bme.mit.semantifyr.oxsts.model.oxsts.XSTS
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.IndentationAwareStringWriter
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.appendIndent
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.indent
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.referencedElementOrNull

object XstsSerializer {

    fun serialize(xsts: XSTS): String {
        val text = indent {
            for (type in xsts.enums) {
                append(type)
            }

            appendLine()

            for (variable in xsts.variables) {
                append(variable)
            }

            appendLine()

            append(xsts.transition, "trans")

            appendLine()

            append(xsts.init, "init")

            appendLine()

            appendLine("env {}")

            appendLine()

            append(xsts.property)
        }

        return text
    }

    private fun IndentationAwareStringWriter.append(variable: Variable) {
        if (variable.isControl) {
            append("ctrl ")
        }
        append("var ${variable.name} : ${variable.typing.name}")
        if (variable.expression != null) {
            append(" = ${variable.expression.serialize()}")
        }
        appendLine()
    }

    private fun IndentationAwareStringWriter.append(enum: Enum) {
        appendIndent("type ${enum.name} :") {
            appendLine(enum.literals.joinToString(",\n") { it.name })
        }
    }

    private fun IndentationAwareStringWriter.append(transition: Transition, kind: String) {
        append(kind)
        if (transition.operation.size >= 1) {
            appendLine(" {")
            indent {
                append(transition.operation.first())
            }
        }
        for (operation in transition.operation.drop(1)) {
            appendLine("} or {")
            indent {
                append(operation)
            }
        }
        appendLine("}")
    }

    private fun IndentationAwareStringWriter.append(property: Property) {
        appendIndent("prop") {
            appendLine(property.invariant.serialize())
        }
    }

    private fun IndentationAwareStringWriter.append(operation: Operation) {
        when (operation) {
            is SequenceOperation -> {
                for (seqOp in operation.operation) {
                    append(seqOp)
                }
            }

            is AssignmentOperation -> appendLine("${operation.reference.serialize()} := ${operation.expression.serialize()};")
            is AssumptionOperation -> appendLine("assume (${operation.expression.serialize()});")
            is HavocOperation -> appendLine("havoc ${operation.referenceExpression.serialize()};")
            is IfOperation -> {
                appendLine("if (${operation.guard.serialize()}) {")
                indent {
                    append(operation.body)
                }
                if (operation.`else` != null) {
                    appendLine("} else {")
                    indent {
                        append(operation.`else`)
                    }
                }
                appendLine("}")
            }

            is ChoiceOperation -> {
                appendLine("choice {")

                if (operation.operation.size >= 1) {
                    indent {
                        append(operation.operation.first())
                    }
                }

                for (choiceOp in operation.operation.drop(1)) {
                    appendLine("} or {")
                    indent {
                        append(choiceOp)
                    }
                }

                if (operation.`else` != null) {
                    appendLine("} else {")
                    indent {
                        append(operation.`else`)
                    }
                }

                appendLine("}")
            }
        }
    }

    val Typing.name: String
        get() = when (this) {
            is IntegerType -> "integer"
            is BooleanType -> "boolean"
            is ReferenceTyping -> {
                when (referencedElementOrNull()) {
                    is Enum -> (referencedElementOrNull() as Enum).name
                    else -> "UNKNOWN_TYPE$$$"
                }
            }
            // FIXME: we should create error markers for cases such as this
            else -> "UNKNOWN_TYPE$$$"
        }

    private fun Expression.serialize(): String = when (this) {
        is OperatorExpression -> serialize()
        is LiteralExpression -> serialize()
        is ReferenceExpression -> serialize()
        else -> "UNKNOWN_EXPRESSION$$$"
    }

    private fun OperatorExpression.serialize(): String = when (this) {
        is AndOperator -> "(${operands[0].serialize()} && ${operands[1].serialize()})"
        is OrOperator -> "(${operands[0].serialize()} || ${operands[1].serialize()})"
        is PlusOperator -> "(${operands[0].serialize()} + ${operands[1].serialize()})"
        is MinusOperator -> "(${operands[0].serialize()} - ${operands[1].serialize()})"
        is EqualityOperator -> "(${operands[0].serialize()} == ${operands[1].serialize()})"
        is InequalityOperator -> "(${operands[0].serialize()} != ${operands[1].serialize()})"
        is LessThanOperator -> "(${operands[0].serialize()} < ${operands[1].serialize()})"
        is LessThanOrEqualsOperator -> "(${operands[0].serialize()} <= ${operands[1].serialize()})"
        is GreaterThanOperator -> "(${operands[0].serialize()} > ${operands[1].serialize()})"
        is GreaterThanOrEqualsOperator -> "(${operands[0].serialize()} >= ${operands[1].serialize()})"
        is NotOperator -> "! (${operands[0].serialize()})"
        else -> "UNKNOWN_EXPRESSION$$$"
    }

    private fun LiteralExpression.serialize(): String = when (this) {
        is LiteralBoolean -> isValue.toString()
        is LiteralInteger -> value.toString()
        else -> "UNKNOWN_EXPRESSION$$$"
    }

    private fun ReferenceExpression.serialize(): String = when (this) {
        is ChainReferenceExpression -> {
            chains.map { it as DeclarationReferenceExpression }.joinToString(".") { it.element.name }
        }

        else -> "UNKNOWN_EXPRESSION$$$"
    }

}
