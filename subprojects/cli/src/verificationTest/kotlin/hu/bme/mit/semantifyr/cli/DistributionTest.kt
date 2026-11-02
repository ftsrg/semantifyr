/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli

import hu.bme.mit.semantifyr.cli.common.DistributionTestBase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.readText

class DistributionTest : DistributionTestBase("semantifyr") {

    private val testModels = Path("../../oxsts-test-models").toAbsolutePath().normalize()

    @Test
    fun `the bundled smoke model passes with the bundled Theta`() {
        val run = run("verify", "-q", "--theta-executor", "shell", "--portfolio", "theta-cegar-expl-pred-combined", "-d", "artifacts", home.resolve("models/smoke").toString())

        assertThat(run.exitCode).describedAs(run.stderr).isZero()
        assertThat(run.stdout.trim()).isEqualTo("PASSED")
        assertThat(run.stderr).doesNotContain("WARNING")
    }

    @Test
    fun `a violated property exits with the failed code and names its report`() {
        val run = run(
            "verify", "--json", "--theta-executor", "shell", "--portfolio", "theta-cegar-expl-pred-combined", "-d", "artifacts",
            "--case-name", "semantifyr::verification::lang::local_vars::LocalVarUninitializedBound",
            testModels.resolve("semantic/local_vars.oxsts").toString(),
        )

        assertThat(run.exitCode).describedAs(run.stderr).isEqualTo(5)
        val line = Json.parseToJsonElement(run.stdout.trim()).jsonObject
        assertThat(line.getValue("exitCode").jsonPrimitive.int).isEqualTo(5)
        val report = reportOf(run)
        val result = Json.parseToJsonElement(report.readText()).jsonObject.getValue("result").jsonObject
        assertThat(result.getValue("verdict").jsonPrimitive.content).isEqualTo("Failed")
        assertThat(report.resolveSibling("witness.json")).exists()
    }

    @Test
    fun `the process exits with the usage code`() {
        assertThat(run("verify", "--portfolio", "no-such-portfolio", "models").exitCode).isEqualTo(2)
    }

    @Test
    fun `the log level option sets the level of the log`() {
        val smoke = home.resolve("models/smoke").toString()

        val quiet = run("list", smoke)
        val verbose = run("--log-level", "info", "list", smoke)

        assertThat(quiet.exitCode).describedAs(quiet.stderr).isZero()
        assertThat(quiet.stderr).doesNotContain("[INFO ]")
        assertThat(verbose.exitCode).describedAs(verbose.stderr).isZero()
        assertThat(verbose.stderr).contains("[INFO ]").contains("list model=")
        assertThat(verbose.stdout).isEqualTo(quiet.stdout)
    }
}
