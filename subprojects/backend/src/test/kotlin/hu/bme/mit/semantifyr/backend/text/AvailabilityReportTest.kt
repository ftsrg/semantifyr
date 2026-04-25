/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.text

import hu.bme.mit.semantifyr.backend.AvailabilityReport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AvailabilityReportTest {

    @Test
    fun `Available is usable`() {
        assertThat(AvailabilityReport.Available.isUsable).isTrue
    }

    @Test
    fun `Degraded is usable but carries a message`() {
        val report = AvailabilityReport.Degraded("falling back to docker")
        assertThat(report.isUsable).isTrue
        assertThat(report.message).isEqualTo("falling back to docker")
    }

    @Test
    fun `Unavailable is not usable and carries a reason and hints`() {
        val report = AvailabilityReport.Unavailable(
            reason = "theta-cli not on PATH",
            hints = listOf("install theta", "set PATH"),
        )
        assertThat(report.isUsable).isFalse
        assertThat(report.reason).isEqualTo("theta-cli not on PATH")
        assertThat(report.hints).containsExactly("install theta", "set PATH")
    }

    @Test
    fun `Unavailable hints default to empty`() {
        val report = AvailabilityReport.Unavailable("nope")
        assertThat(report.hints).isEmpty()
    }
}
