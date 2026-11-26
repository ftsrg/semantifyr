/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli

import hu.bme.mit.semantifyr.cli.common.DistributionTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DistributionTest : DistributionTestBase("semantifyr") {

    @Test
    fun `the bundled smoke model passes`() {
        val run = run("verify", "-q", "--theta-executor", "shell", "--portfolio", "theta-cegar-expl-pred-combined", "-d", "artifacts", home.resolve("models/smoke").toString())

        assertThat(run.exitCode).describedAs(run.stderr).isZero()
        assertThat(run.stdout.trim()).isEqualTo("PASSED")
        assertThat(run.stderr).doesNotContain("WARNING")
    }
}
