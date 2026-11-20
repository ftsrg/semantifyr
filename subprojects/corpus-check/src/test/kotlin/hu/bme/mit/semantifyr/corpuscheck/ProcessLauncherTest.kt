/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.corpuscheck

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

class ProcessLauncherTest {

    private val artifactDirectory = Path("artifacts", "Model-1")

    @Test
    fun `the launcher verifies the named cases of the model as json lines into the artifact directory within the timeout`() {
        val launcher = ProcessLauncher(Path("semantifyr-gamma"), Path("artifacts"), timeoutSeconds = 30)

        val command = launcher.command(Path("Model.gamma"), listOf("Model.First", "Model.Second"), artifactDirectory)

        assertThat(command).containsExactly(
            "semantifyr-gamma",
            "verify",
            "--json",
            "-d",
            artifactDirectory.toString(),
            "--timeout-seconds",
            "30",
            "--case-name",
            "Model.First",
            "--case-name",
            "Model.Second",
            "Model.gamma",
        )
    }

    @Test
    fun `without a timeout and case names the launcher verifies every case within its default timeout`() {
        val launcher = ProcessLauncher(Path("semantifyr-gamma"), Path("artifacts"), timeoutSeconds = null)

        val command = launcher.command(Path("Model.gamma"), emptyList(), artifactDirectory)

        assertThat(command).containsExactly(
            "semantifyr-gamma",
            "verify",
            "--json",
            "-d",
            artifactDirectory.toString(),
            "Model.gamma",
        )
    }
}
