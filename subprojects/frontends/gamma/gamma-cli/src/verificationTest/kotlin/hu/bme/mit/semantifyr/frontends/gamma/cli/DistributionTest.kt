/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.cli

import hu.bme.mit.semantifyr.cli.common.DistributionTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DistributionTest : DistributionTestBase("semantifyr-gamma") {

    @Test
    fun `the bundled smoke model is verified with the bundled Theta and its trace written`() {
        val run = run(
            "verify", "--json", "--theta-executor", "shell", "--portfolio", "theta-bounded-bmc", "-d", "artifacts",
            "--case-name", "Simple.WorkerStatechartOperationalReachable",
            home.resolve("models/smoke/Simple.gamma").toString(),
        )

        assertThat(run.exitCode).describedAs(run.stderr).isZero()
        assertThat(reportOf(run).resolveSibling("trace.get")).exists()
    }

    @Test
    fun `the distribution carries the Gamma semantic library`() {
        assertThat(home.resolve("libraries/gamma")).isDirectoryContaining("glob:**.oxsts")
    }
}
