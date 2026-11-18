/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

@Serializable
data class RatioOfMeans(
    val ratio: Double,
    val lower: Double?,
    val upper: Double?,
    val confidenceLevel: Double,
    val baselineCount: Int,
    val comparedCount: Int,
) {
    override fun toString(): String {
        val confidence = "%.0f%%".format(Locale.ROOT, confidenceLevel * 100)
        if (lower == null || upper == null) {
            return "%.3f, its %s interval unbounded".format(Locale.ROOT, ratio, confidence)
        }
        return "%.3f [%.3f, %.3f] at %s".format(Locale.ROOT, ratio, lower, upper, confidence)
    }
}

// Fieller's interval as Kalibera and Jones give it in section 10.1
fun ratioOfMeans(
    baseline: List<Double>,
    compared: List<Double>,
    confidenceLevel: Double = 0.95,
): RatioOfMeans {
    require(baseline.size >= 2 && compared.size >= 2) {
        "a ratio's interval needs two values on each side"
    }
    require(confidenceLevel > 0 && confidenceLevel < 1) {
        "a confidence level lies between 0 and 1"
    }
    val baselineMean = baseline.average()
    val comparedMean = compared.average()
    val baselineHalfWidth = halfWidth(baseline, confidenceLevel)
    val comparedHalfWidth = halfWidth(compared, confidenceLevel)
    val product = baselineMean * comparedMean
    val denominator = baselineMean * baselineMean - baselineHalfWidth * baselineHalfWidth
    val comparedTerm = comparedMean * comparedMean - comparedHalfWidth * comparedHalfWidth
    val discriminant = product * product - denominator * comparedTerm
    val ratio = comparedMean / baselineMean
    // the baseline's own interval holds zero, so the ratio is bounded on no side
    if (denominator <= 0) {
        return RatioOfMeans(ratio, null, null, confidenceLevel, baseline.size, compared.size)
    }
    return RatioOfMeans(
        ratio = ratio,
        lower = (product - sqrt(discriminant)) / denominator,
        upper = (product + sqrt(discriminant)) / denominator,
        confidenceLevel = confidenceLevel,
        baselineCount = baseline.size,
        comparedCount = compared.size,
    )
}

private fun halfWidth(values: List<Double>, confidenceLevel: Double): Double {
    val mean = values.average()
    val variance = values.sumOf {
        (it - mean) * (it - mean)
    } / (values.size - 1)
    val quantile = studentQuantile(1 - (1 - confidenceLevel) / 2, values.size - 1)
    return quantile * sqrt(variance / values.size)
}

internal fun studentQuantile(probability: Double, degreesOfFreedom: Int): Double {
    var low = 0.0
    var high = 1.0
    while (studentDistribution(high, degreesOfFreedom) < probability) {
        high *= 2
    }
    repeat(200) {
        val middle = (low + high) / 2
        if (studentDistribution(middle, degreesOfFreedom) < probability) {
            low = middle
        } else {
            high = middle
        }
    }
    return (low + high) / 2
}

private fun studentDistribution(value: Double, degreesOfFreedom: Int): Double {
    val point = degreesOfFreedom / (degreesOfFreedom + value * value)
    val tail = regularizedIncompleteBeta(point, degreesOfFreedom / 2.0, 0.5) / 2
    if (value >= 0) {
        return 1 - tail
    }
    return tail
}

// the continued fraction of Numerical Recipes
private fun regularizedIncompleteBeta(
    point: Double,
    firstShape: Double,
    secondShape: Double,
): Double {
    if (point <= 0) {
        return 0.0
    }
    if (point >= 1) {
        return 1.0
    }
    val logBeta = logGamma(firstShape) + logGamma(secondShape) - logGamma(firstShape + secondShape)
    val front = exp(firstShape * ln(point) + secondShape * ln(1 - point) - logBeta)
    if (point < (firstShape + 1) / (firstShape + secondShape + 2)) {
        return front * betaContinuedFraction(point, firstShape, secondShape) / firstShape
    }
    return 1 - front * betaContinuedFraction(1 - point, secondShape, firstShape) / secondShape
}

private const val SMALLEST_TERM = 1e-300

private fun betaContinuedFraction(
    point: Double,
    firstShape: Double,
    secondShape: Double,
): Double {
    var numeratorTerm = 1.0
    var denominatorTerm = 1 / nonZero(1 - (firstShape + secondShape) * point / (firstShape + 1))
    var fraction = denominatorTerm
    for (step in 1..1000) {
        val shapeStep = firstShape + 2 * step
        val even = step * (secondShape - step) * point / ((shapeStep - 1) * shapeStep)
        denominatorTerm = 1 / nonZero(1 + even * denominatorTerm)
        numeratorTerm = nonZero(1 + even / numeratorTerm)
        fraction *= denominatorTerm * numeratorTerm
        val odd = -(firstShape + step) * (firstShape + secondShape + step) * point / (shapeStep * (shapeStep + 1))
        denominatorTerm = 1 / nonZero(1 + odd * denominatorTerm)
        numeratorTerm = nonZero(1 + odd / numeratorTerm)
        val change = denominatorTerm * numeratorTerm
        fraction *= change
        if (abs(change - 1) < 1e-15) {
            break
        }
    }
    return fraction
}

private fun nonZero(value: Double): Double {
    if (abs(value) < SMALLEST_TERM) {
        return SMALLEST_TERM
    }
    return value
}

private val lanczosCoefficients = listOf(
    76.18009172947146,
    -86.50532032941677,
    24.01409824083091,
    -1.231739572450155,
    0.1208650973866179e-2,
    -0.5395239384953e-5,
)

private fun logGamma(value: Double): Double {
    var term = value + 5.5
    term -= (value + 0.5) * ln(term)
    var series = 1.000000000190015
    var denominator = value
    for (coefficient in lanczosCoefficients) {
        denominator += 1
        series += coefficient / denominator
    }
    return -term + ln(2.5066282746310005 * series / value)
}
