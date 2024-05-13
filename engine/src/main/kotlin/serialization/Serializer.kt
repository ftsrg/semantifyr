package hu.bme.mit.gamma.oxsts.engine.serialization

import hu.bme.mit.gamma.oxsts.engine.utils.referencedElement
import hu.bme.mit.gamma.oxsts.model.oxsts.AndOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.BooleanType
import hu.bme.mit.gamma.oxsts.model.oxsts.ChainReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.DeclarationReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Enum
import hu.bme.mit.gamma.oxsts.model.oxsts.EqualityOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.Expression
import hu.bme.mit.gamma.oxsts.model.oxsts.GreaterThanOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.GreaterThanOrEqualsOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.IfOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.InequalityOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.IntegerType
import hu.bme.mit.gamma.oxsts.model.oxsts.LessThanOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.LessThanOrEqualsOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.gamma.oxsts.model.oxsts.MinusOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.NotOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.Operation
import hu.bme.mit.gamma.oxsts.model.oxsts.OperatorExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.OrOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.PlusOperator
import hu.bme.mit.gamma.oxsts.model.oxsts.Property
import hu.bme.mit.gamma.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.ReferenceTyping
import hu.bme.mit.gamma.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.Transition
import hu.bme.mit.gamma.oxsts.model.oxsts.Typing
import hu.bme.mit.gamma.oxsts.model.oxsts.Variable
import hu.bme.mit.gamma.oxsts.model.oxsts.XSTS

class IndentationAwareStringWriter(
    private val indentation: String
) {
    private val stringBuilder = StringBuilder()

    private var indentLevel = 0

    fun appendLine() {
        stringBuilder.appendLine()
    }

    fun append(string: String) {
        val indentedString = string.prependIndent(indentation.repeat(indentLevel))
        stringBuilder.append(indentedString)
    }

    fun appendLine(string: String) {
        val indentedString = string.prependIndent(indentation.repeat(indentLevel))
        stringBuilder.appendLine(indentedString)
    }

    inline fun indent(body: IndentationAwareStringWriter.() -> Unit) {
        indent()
        body()
        outdent()
    }

    fun indent() {
        indentLevel++
    }

    fun outdent() {
        indentLevel--
    }

    override fun toString() = stringBuilder.toString()

}

inline fun indent(indentation: String = "    ", body: IndentationAwareStringWriter.() -> Unit): String {
    val writer = IndentationAwareStringWriter(indentation)
    writer.body()
    return writer.toString()
}

object Serializer {
    fun serializeProperty(xsts: XSTS): String {
        val text = indent {
            append(xsts.property)
        }

        return text
    }

    fun serialize(xsts: XSTS, includeProperty: Boolean = true): String {
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

            if (includeProperty) {
                appendLine()

                append(xsts.property)
            }
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
        appendLine("type ${enum.name} : {")
        indent {
            appendLine(enum.literals.joinToString(",\n") { it.name })
        }
        appendLine("}")
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
        appendLine("prop {")
        indent {
            appendLine(property.invariant.serialize())
        }
        appendLine("}")
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
                when (referencedElement) {
                    is Enum -> (referencedElement as Enum).name
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
