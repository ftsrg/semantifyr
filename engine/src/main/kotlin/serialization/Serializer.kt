package hu.bme.mit.gamma.oxsts.engine.serialization

import hu.bme.mit.gamma.oxsts.model.oxsts.*
import java.io.File
import java.io.Writer
import java.lang.IllegalArgumentException

class FileSerializer(
    private val path: String
) {

    fun serialize(type: Type) {
        File(path).writer(Charsets.UTF_8).use { writer ->

            for (variable in type.variables) {
                writer.write("var ${variable.name} : ${variable.typing.serialize()} \n")
            }

            val init = type.initTransition.first().operation.first()
            writer.write("""
                init {
            """.trimIndent())
            writer.write("${init.serialize()}\n")
            writer.write("""
                }
            """.trimIndent())
            //iter.write("var ${transition.name} : ${transition.typing.serialize()}")

        }
    }

    fun VariableTyping.serialize(): String {
        return when (this) {
            is BooleanType -> "Boolean"
            is IntegerType -> "Integer"
            is EnumType -> "Enum"
            else -> throw IllegalArgumentException("Unknown type $this")
        }
    }

    fun Operation.serialize(): String {
        return when (this) {
            is SequenceOperation -> {
                this.operation.map { it.serialize() }.joinToString("\n")
            }
            is AssignmentOperation -> {
                "${reference.serialize()} := ${expression.serialize()}"
            }
            else -> ""
        }
    }

    fun Expression.serialize(): String {
        return when(this) {
            is DeclarationReferenceExpression -> "name"
            else -> "error"
        }
    }

}
