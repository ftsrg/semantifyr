/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import hu.bme.mit.semantifyr.backend.witness.Fraction
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
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

private val INT_RANGE = BigInteger.valueOf(Int.MIN_VALUE.toLong())..BigInteger.valueOf(Int.MAX_VALUE.toLong())

object FractionSerializer : KSerializer<Fraction> {
    override val descriptor = PrimitiveSerialDescriptor("hu.bme.mit.semantifyr.Fraction", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Fraction) {
        encoder.encodeSerializableValue(ReportValueSerializer, ReportValue.of(value))
    }

    override fun deserialize(decoder: Decoder): Fraction {
        try {
            return decoder.decodeSerializableValue(ReportValueSerializer).toFraction()
        } catch (exception: NumberFormatException) {
            throw SerializationException(exception.message, exception)
        }
    }
}

@Serializable(with = ReportValueSerializer::class)
sealed interface ReportValue {
    data class Bool(val value: Boolean) : ReportValue

    data class Integer(val value: Int) : ReportValue

    data class Real(val value: Double) : ReportValue

    data class Text(val value: String) : ReportValue

    data object Null : ReportValue

    data class Array(val values: List<ReportValue>) : ReportValue

    data class Record(val fields: Map<String, ReportValue>) : ReportValue

    fun toFraction(): Fraction {
        return when (this) {
            is Integer -> Fraction.of(value.toLong())
            is Text -> Fraction.parse(value)
            else -> throw NumberFormatException("$this is no amount of time")
        }
    }

    companion object {
        fun of(amount: Fraction): ReportValue {
            if (amount.isWhole && amount.numerator in INT_RANGE) {
                return Integer(amount.numerator.intValueExact())
            }
            return Text(amount.toString())
        }
    }
}

object ReportValueSerializer : KSerializer<ReportValue> {
    override val descriptor = SerialDescriptor("hu.bme.mit.semantifyr.ReportValue", JsonElement.serializer().descriptor)

    private val arraySerializer = ListSerializer(this)

    private val recordSerializer = MapSerializer(String.serializer(), this)

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: ReportValue) {
        when (value) {
            is ReportValue.Bool -> encoder.encodeBoolean(value.value)
            is ReportValue.Integer -> encoder.encodeInt(value.value)
            is ReportValue.Real -> encoder.encodeDouble(value.value)
            is ReportValue.Text -> encoder.encodeString(value.value)
            is ReportValue.Null -> encoder.encodeNull()
            is ReportValue.Array -> encoder.encodeSerializableValue(arraySerializer, value.values)
            is ReportValue.Record -> encoder.encodeSerializableValue(recordSerializer, value.fields)
        }
    }

    override fun deserialize(decoder: Decoder): ReportValue {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("a report value is read from JSON only")
        return read(jsonDecoder.decodeJsonElement())
    }

    private fun read(element: JsonElement): ReportValue {
        return when (element) {
            is JsonNull -> ReportValue.Null
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
        if (integer == null) {
            return ReportValue.Real(primitive.content.toDouble())
        }
        if (integer !in INT_RANGE) {
            throw SerializationException("the integer $integer is outside 32 bits")
        }
        return ReportValue.Integer(integer.intValueExact())
    }
}
