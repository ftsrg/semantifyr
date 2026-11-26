/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json

@Serializable
data class ReportField(
    val name: String,
    val type: String,
    val nullable: Boolean,
    val values: List<String>,
    val fields: List<ReportField>,
)

private val json = Json {
    prettyPrint = true
}

private fun typeOf(descriptor: SerialDescriptor): String {
    val serialName = descriptor.serialName.removeSuffix("?")
    if (serialName == "kotlin.time.Duration") {
        return "duration"
    }
    if (serialName == "kotlin.time.Instant") {
        return "instant"
    }
    if (serialName == FractionSerializer.descriptor.serialName) {
        return "fraction"
    }
    if (serialName == ReportValueSerializer.descriptor.serialName) {
        return "value"
    }
    return when (descriptor.kind) {
        PrimitiveKind.STRING, PrimitiveKind.CHAR -> {
            "string"
        }
        PrimitiveKind.BOOLEAN -> {
            "boolean"
        }
        PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG -> {
            "integer"
        }
        PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> {
            "number"
        }
        SerialKind.ENUM -> {
            "enum"
        }
        StructureKind.LIST -> {
            "list${heldTypeOf(descriptor)}"
        }
        StructureKind.MAP -> {
            "map${heldTypeOf(descriptor)}"
        }
        else -> {
            "object"
        }
    }
}

private fun heldTypeOf(descriptor: SerialDescriptor): String {
    val held = heldDescriptorOf(descriptor)
    if (held.kind == StructureKind.CLASS) {
        return ""
    }
    return " of ${typeOf(held)}"
}

// a list or a map shows the fields of what it holds
private fun heldDescriptorOf(descriptor: SerialDescriptor): SerialDescriptor {
    return when (descriptor.kind) {
        StructureKind.LIST -> {
            descriptor.getElementDescriptor(0)
        }
        StructureKind.MAP -> {
            descriptor.getElementDescriptor(1)
        }
        else -> {
            descriptor
        }
    }
}

private fun fieldsOf(descriptor: SerialDescriptor, enclosing: Set<String>): List<ReportField> {
    val held = heldDescriptorOf(descriptor)
    if (held.kind != StructureKind.CLASS || held.serialName in enclosing) {
        return emptyList()
    }
    return (0..<held.elementsCount).map {
        val element = held.getElementDescriptor(it)
        val elementHeld = heldDescriptorOf(element)
        ReportField(
            name = held.getElementName(it),
            type = typeOf(element),
            nullable = element.isNullable,
            values = if (elementHeld.kind == SerialKind.ENUM) {
                (0..<elementHeld.elementsCount).map { index ->
                    elementHeld.getElementName(index)
                }
            } else {
                emptyList()
            },
            fields = fieldsOf(element, enclosing + held.serialName),
        )
    }
}

private fun fieldsOf(descriptor: SerialDescriptor): String {
    return json.encodeToString(fieldsOf(descriptor, emptySet()))
}

fun main(arguments: Array<String>) {
    val output = when (val reference = arguments.single()) {
        "report-fields" -> {
            fieldsOf(VerificationReport.serializer().descriptor)
        }
        else -> {
            error("No reference $reference")
        }
    }
    println(output)
}
