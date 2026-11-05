/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.cli

import hu.bme.mit.semantifyr.cli.common.DistributionTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DistributionTest : DistributionTestBase("semantifyr-sysml") {

    @Test
    fun `the bundled smoke model is verified with the bundled Theta and its trace written`() {
        assumeNodeOnPath()

        val run = run(
            "verify", "--json", "--theta-executor", "shell", "--portfolio", "theta-bounded-bmc", "-d", "artifacts",
            "--case-name", "semantifyr::sysml::model::LampLitOnlyWhileSwitchOnViolated",
            home.resolve("models/smoke/room.sysml").toString(),
        )

        assertThat(run.exitCode).describedAs(run.stderr).isEqualTo(5)
        assertThat(reportOf(run).resolveSibling("trace.sysml")).exists()
    }

    @Test
    fun `the distribution carries the SysML v2 semantic library`() {
        assertThat(home.resolve("libraries/sysmlv2")).isDirectoryContaining("glob:**.oxsts")
    }
}
