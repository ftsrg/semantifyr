/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.common

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.testing.test
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

class RootCommandTest {

    private class ProbeCommand : SuspendingCliktCommand("probe") {
        var enabledLevels = emptyList<Level>()

        override suspend fun run() {
            val logger = LoggerFactory.getLogger(RootCommandTest::class.java)
            enabledLevels = Level.entries.filter {
                logger.isEnabledForLevel(it)
            }
        }
    }

    private class TestRootCommand(probe: ProbeCommand) : RootCommand("test", "A test root command.", listOf(probe))

    private suspend fun enabledLevelsAfter(vararg arguments: String): List<Level> {
        val probe = ProbeCommand()
        val command = TestRootCommand(probe)
        val result = command.test(arguments.toList()) {
            command.execute(it)
        }
        assertThat(result.statusCode).describedAs(result.output).isZero()
        return probe.enabledLevels
    }

    @Test
    suspend fun `the default log level is warn`() {
        assertThat(enabledLevelsAfter("probe")).containsExactlyInAnyOrder(Level.ERROR, Level.WARN)
    }

    @ParameterizedTest
    @CsvSource(
        "ERROR, ERROR",
        "info, INFO",
        "DEBUG, DEBUG",
        "trace, TRACE",
    )
    suspend fun `the log level option sets the root level of the running configuration`(
        option: String,
        finest: Level,
    ) {
        val expected = Level.entries.filter {
            it.toInt() >= finest.toInt()
        }

        assertThat(enabledLevelsAfter("--log-level", option, "probe")).containsExactlyInAnyOrderElementsOf(expected)
    }

    @Test
    suspend fun `an unknown log level is a usage error`() {
        val command = TestRootCommand(ProbeCommand())

        val result = command.test(listOf("--log-level", "loud", "probe")) {
            command.execute(it)
        }

        assertThat(result.statusCode).isEqualTo(ExitCode.Usage.code)
        assertThat(result.stderr).contains("--log-level")
    }
}
