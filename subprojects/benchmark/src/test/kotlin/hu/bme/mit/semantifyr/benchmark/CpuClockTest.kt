/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CpuClockTest {

    @Test
    fun `reaped children time is cutime plus cstime in hundredths of a second`() {
        val stat = "4242 (java (main) x) S 1 4242 4242 0 -1 4194560 1000 2000 0 0 700 300 150 25 20 0 40 0 12345 0 0"

        assertThat(CpuClock.parseChildrenCpuTime(stat)).isEqualTo(1750.milliseconds)
    }

    @Test
    fun `a stat line without the children fields has no children time`() {
        assertThat(CpuClock.parseChildrenCpuTime("4242 (java) S 1 4242")).isNull()
    }

    @Test
    fun `a sample difference subtracts both clocks`() {
        val earlier = CpuSample(process = 2.seconds, children = 1.seconds)
        val later = CpuSample(process = 5.seconds, children = 4.seconds)

        val difference = later - earlier

        assertThat(difference.process).isEqualTo(3.seconds)
        assertThat(difference.children).isEqualTo(3.seconds)
        assertThat(difference.total).isEqualTo(6.seconds)
    }

    @Test
    fun `without a children clock the total is the process time`() {
        val difference = CpuSample(process = 5.seconds, children = null) - CpuSample(process = 2.seconds, children = null)

        assertThat(difference.total).isEqualTo(3.seconds)
    }
}
