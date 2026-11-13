/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.website

import hu.bme.mit.semantifyr.cli.common.ExitCode
import hu.bme.mit.semantifyr.live.backend.VerificationConfig
import hu.bme.mit.semantifyr.live.backend.liveImageBackends
import hu.bme.mit.semantifyr.verifier.VerificationReport
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json

@Serializable
data class ExitCodeEntry(
    val code: Int,
    val meaning: String,
)

@Serializable
data class ReportField(
    val name: String,
    val type: String,
    val nullable: Boolean,
    val values: List<String>,
    val fields: List<ReportField>,
)

@Serializable
data class LiveFacts(
    val backends: List<String>,
    val verificationTimeoutSeconds: Long,
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
            "list"
        }
        StructureKind.MAP -> {
            "map"
        }
        else -> {
            "object"
        }
    }
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

private fun exitCodes(): String {
    val entries = ExitCode.entries.map {
        ExitCodeEntry(it.code, it.meaning)
    }
    return json.encodeToString(entries)
}

private fun reportFields(): String {
    return json.encodeToString(fieldsOf(VerificationReport.serializer().descriptor, emptySet()))
}

private fun live(): String {
    val facts = LiveFacts(
        backends = liveImageBackends,
        verificationTimeoutSeconds = VerificationConfig().timeout.inWholeSeconds,
    )
    return json.encodeToString(facts)
}

fun main(arguments: Array<String>) {
    val output = when (val reference = arguments.single()) {
        "exit-codes" -> {
            exitCodes()
        }
        "report-fields" -> {
            reportFields()
        }
        "live" -> {
            live()
        }
        else -> {
            error("No reference $reference")
        }
    }
    println(output)
}
