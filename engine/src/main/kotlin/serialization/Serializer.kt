package hu.bme.mit.gamma.oxsts.engine.serialization

import hu.bme.mit.gamma.oxsts.model.oxsts.*
import hu.bme.mit.gamma.oxsts.model.oxsts.Enum
import hu.bme.mit.gamma.oxsts.model.oxsts.Target

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

            append(xsts.transition, "tran")

            append(xsts.init, "init")

            append(xsts.property)
        }

        return text
    }

    fun IndentationAwareStringWriter.append(variable: Variable) {
        append("var ${variable.name} : ${variable.typing.name} ")
        if (variable.expression != null) {
            append(":= ${variable.expression.serialize()}")
        }
        appendLine(";")
    }

    fun IndentationAwareStringWriter.append(enum: Enum) {
        appendLine("type ${enum.name} : {")
        indent {
            appendLine(enum.literals.map { it.name }.joinToString(", \n"))
        }
        appendLine("}")
    }

    fun IndentationAwareStringWriter.append(transition: Transition, kind: String) {
        append(kind)
        if (transition.operation.size >= 1) {
            append(transition.operation.first())
        }
        for (operation in transition.operation.drop(1)) {
            append(" or")
            append(operation)
        }
        appendLine()
    }

    fun IndentationAwareStringWriter.append(property: Property) {
        appendLine("prop {")
        indent {
            appendLine(property.invariant.serialize())
        }
        appendLine("}")
    }

    fun IndentationAwareStringWriter.append(operation: Operation) {
        appendLine(" {")
        indent {
            appendLine(operation.serialize())
        }
        appendLine("}")
    }

    val VariableTyping.name: String
        get() = when (this) {
        is IntegerType -> "Integer"
        is BooleanType -> "Boolean"
        is VariableTypeReference -> {
            val reference = this.reference

            when (reference) {
                is Enum -> reference.name
                else -> "UNKNOWN_TYPE$$$"
            }
        }
        else -> "UNKNOWN_TYPE$$$"
    }

    fun Expression.serialize(): String = when (this) {
        is OperatorExpression -> serialize()
        is LiteralExpression -> serialize()
        is ReferenceExpression -> serialize()
        else -> "UNKNOWN_EXPRESSION$$$"
    }

    fun OperatorExpression.serialize(): String = when (this) {
        is AndOperator -> "${operands[0].serialize()} && ${operands[1].serialize()}"
        is OrOperator -> "${operands[0].serialize()} || ${operands[1].serialize()}"
        is PlusOperator -> "${operands[0].serialize()} + ${operands[1].serialize()}"
        is MinusOperator -> "${operands[0].serialize()} - ${operands[1].serialize()}"
        is EqualityOperator -> "${operands[0].serialize()} == ${operands[1].serialize()}"
        is InequalityOperator -> "${operands[0].serialize()} != ${operands[1].serialize()}"
        is NotOperator -> "! ${operands[0].serialize()}"
        else -> "UNKNOWN_EXPRESSION$$$"
    }

    fun LiteralExpression.serialize(): String = when(this) {
        is LiteralNothing -> "__NOTHING__"
        is LiteralBoolean -> if (isValue) "true" else "false"
        is LiteralInteger -> value.toString()
        else -> "UNKNOWN_EXPRESSION$$$"
    }

    fun ReferenceExpression.serialize(): String = when (this) {
        is DeclarationReferenceExpression -> element.name
        else -> "UNKNOWN_EXPRESSION$$$"
    }

    fun Operation.serialize(): String = when (this) {
        is SequenceOperation -> operation.map { it.serialize() }.joinToString("\n")
        is AssignmentOperation -> "${reference.serialize()} := ${expression.serialize()}"
        is AssumptionOperation -> "assume (${expression.serialize()})"
        is HavocOperation -> "havoc (${referenceExpression.serialize()}"
        is IfOperation -> ""
        is ChoiceOperation -> {
            var result = "choice "

            if (operation.size >= 1) {
                result += """
                    {
                        ${operation.first().serialize()}
                    }
                """.trimIndent()
            }

            for (operation in operation.drop(1)) {
                result += """
                     or {
                        ${operation.serialize()}
                    }
                """.trimIndent()
            }

            result
        }
        else -> "UNKNOWN_OPERATION$$$"
    }
}
