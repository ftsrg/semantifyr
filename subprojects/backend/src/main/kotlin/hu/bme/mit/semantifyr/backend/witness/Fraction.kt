/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.witness

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigInteger

// the largest integer every JSON reader holds exactly, an IEEE double's 53 bits
val MAX_EXACT_JSON_INTEGER: BigInteger = BigInteger.TWO.pow(53) - BigInteger.ONE

fun isExactJsonInteger(value: BigInteger): Boolean {
    return value.abs() <= MAX_EXACT_JSON_INTEGER
}

@Serializable(with = FractionSerializer::class)
class Fraction private constructor(
    val numerator: BigInteger,
    val denominator: BigInteger,
) {
    val isWhole: Boolean
        get() = denominator == BigInteger.ONE

    operator fun plus(other: Fraction): Fraction {
        return of(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator)
    }

    override fun equals(other: Any?): Boolean {
        return other is Fraction && numerator == other.numerator && denominator == other.denominator
    }

    override fun hashCode(): Int {
        return 31 * numerator.hashCode() + denominator.hashCode()
    }

    override fun toString(): String {
        if (isWhole) {
            return numerator.toString()
        }
        return "$numerator/$denominator"
    }

    companion object {
        val ZERO = of(BigInteger.ZERO)

        fun of(numerator: BigInteger, denominator: BigInteger = BigInteger.ONE): Fraction {
            require(denominator.signum() != 0) {
                "a fraction has a nonzero denominator"
            }
            val divisor = numerator.gcd(denominator).let {
                if (denominator.signum() < 0) {
                    it.negate()
                } else {
                    it
                }
            }
            return Fraction(numerator / divisor, denominator / divisor)
        }

        fun of(numerator: Long, denominator: Long = 1): Fraction {
            return of(numerator.toBigInteger(), denominator.toBigInteger())
        }

        fun parse(text: String): Fraction {
            val parts = text.split('/')
            val numerator = parts[0].toBigIntegerOrNull()
            val denominator = parts.getOrNull(1)?.toBigIntegerOrNull() ?: BigInteger.ONE
            if (numerator == null || parts.size > 2 || denominator.signum() == 0) {
                throw NumberFormatException("'$text' is no fraction")
            }
            return of(numerator, denominator)
        }
    }
}

object FractionSerializer : KSerializer<Fraction> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("hu.bme.mit.semantifyr.Fraction", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Fraction) {
        if (value.isWhole && isExactJsonInteger(value.numerator)) {
            encoder.encodeLong(value.numerator.toLong())
        } else {
            encoder.encodeString(value.toString())
        }
    }

    override fun deserialize(decoder: Decoder): Fraction {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("a fraction is read from JSON only")
        val primitive: JsonPrimitive = jsonDecoder.decodeJsonElement().jsonPrimitive
        try {
            return Fraction.parse(primitive.content)
        } catch (exception: NumberFormatException) {
            throw SerializationException(exception.message, exception)
        }
    }
}
