/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import hu.bme.mit.semantifyr.backend.witness.Fraction
import hu.bme.mit.semantifyr.backend.witness.FractionSerializer
import hu.bme.mit.semantifyr.backend.witness.isExactJsonInteger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.math.BigInteger

@Serializable(with = ReportValueSerializer::class)
sealed interface ReportValue {
    data class Bool(val value: Boolean) : ReportValue

    data class Integer(val value: BigInteger) : ReportValue

    data class Rational(val value: Fraction) : ReportValue

    data class Real(val value: Double) : ReportValue

    // a JSON string whose kind, an enum literal, an instance path or an integer too large for a number, the report's type list gives
    data class Text(val value: String) : ReportValue

    data class EnumLiteral(val name: String) : ReportValue

    data class Instance(val path: String) : ReportValue

    data object NoInstance : ReportValue

    data class Array(val values: List<ReportValue>) : ReportValue

    data class Record(val fields: Map<String, ReportValue>) : ReportValue
}

object ReportValueSerializer : KSerializer<ReportValue> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    private val arraySerializer = ListSerializer(this)

    private val recordSerializer = MapSerializer(String.serializer(), this)

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: ReportValue) {
        when (value) {
            is ReportValue.Bool -> encoder.encodeBoolean(value.value)
            is ReportValue.Integer -> encodeInteger(encoder, value.value)
            is ReportValue.Rational -> FractionSerializer.serialize(encoder, value.value)
            is ReportValue.Real -> encoder.encodeDouble(value.value)
            is ReportValue.Text -> encoder.encodeString(value.value)
            is ReportValue.EnumLiteral -> encoder.encodeString(value.name)
            is ReportValue.Instance -> encoder.encodeString(value.path)
            is ReportValue.NoInstance -> encoder.encodeNull()
            is ReportValue.Array -> encoder.encodeSerializableValue(arraySerializer, value.values)
            is ReportValue.Record -> encoder.encodeSerializableValue(recordSerializer, value.fields)
        }
    }

    private fun encodeInteger(encoder: Encoder, value: BigInteger) {
        if (isExactJsonInteger(value)) {
            encoder.encodeLong(value.toLong())
        } else {
            encoder.encodeString(value.toString())
        }
    }

    override fun deserialize(decoder: Decoder): ReportValue {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("a report value is read from JSON only")
        return read(jsonDecoder.decodeJsonElement())
    }

    private fun read(element: JsonElement): ReportValue {
        return when (element) {
            is JsonNull -> ReportValue.NoInstance
            is JsonArray -> ReportValue.Array(
                element.map {
                    read(it)
                },
            )
            is JsonObject -> ReportValue.Record(
                element.mapValues {
                    read(it.value)
                },
            )
            is JsonPrimitive -> readPrimitive(element)
        }
    }

    private fun readPrimitive(primitive: JsonPrimitive): ReportValue {
        if (primitive.isString) {
            return ReportValue.Text(primitive.content)
        }
        val boolean = primitive.booleanOrNull
        if (boolean != null) {
            return ReportValue.Bool(boolean)
        }
        val integer = primitive.content.toBigIntegerOrNull()
        if (integer != null) {
            return ReportValue.Integer(integer)
        }
        return ReportValue.Real(primitive.content.toDouble())
    }
}
