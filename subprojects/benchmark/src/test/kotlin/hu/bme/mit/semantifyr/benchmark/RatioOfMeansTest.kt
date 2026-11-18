/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class RatioOfMeansTest {

    private val baseline = listOf(10.0, 11.0, 9.0, 10.0, 10.0)
    private val compared = listOf(7.0, 8.5, 6.5, 7.5, 8.0)

    @Test
    fun `the interval of a worked example follows Kalibera and Jones' equation`() {
        val relation = ratioOfMeans(baseline, compared)

        assertThat(relation.ratio).isEqualTo(0.75)
        assertThat(relation.lower).isCloseTo(0.637022, within(1e-6))
        assertThat(relation.upper).isCloseTo(0.874630, within(1e-6))
        assertThat(relation.confidenceLevel).isEqualTo(0.95)
    }

    @Test
    fun `the interval's ends are where Fieller's quadratic is zero`() {
        val relation = ratioOfMeans(baseline, compared, 0.9)
        val baselineHalfWidth = studentQuantile(0.95, 4) * sqrt(0.5 / 5)
        val comparedHalfWidth = studentQuantile(0.95, 4) * sqrt(0.625 / 5)

        for (end in listOf(relation.lower!!, relation.upper!!)) {
            val difference = 7.5 - end * 10.0
            assertThat(difference * difference).isCloseTo(comparedHalfWidth * comparedHalfWidth + end * end * baselineHalfWidth * baselineHalfWidth, within(1e-9))
        }
    }

    @Test
    fun `the Student quantiles match the published table`() {
        assertThat(studentQuantile(0.975, 1)).isCloseTo(12.706205, within(1e-5))
        assertThat(studentQuantile(0.975, 4)).isCloseTo(2.776445, within(1e-5))
        assertThat(studentQuantile(0.95, 4)).isCloseTo(2.131847, within(1e-5))
        assertThat(studentQuantile(0.995, 9)).isCloseTo(3.249836, within(1e-5))
        assertThat(studentQuantile(0.975, 1000000)).isCloseTo(1.959966, within(1e-5))
    }

    @Test
    fun `a baseline whose interval holds zero leaves the ratio unbounded`() {
        val relation = ratioOfMeans(listOf(1.0, -1.0, 2.0, -2.0, 0.5), compared)

        assertThat(relation.lower).isNull()
        assertThat(relation.upper).isNull()
    }
}
