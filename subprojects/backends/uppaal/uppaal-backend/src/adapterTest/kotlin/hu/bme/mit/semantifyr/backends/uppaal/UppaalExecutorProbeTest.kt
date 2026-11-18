/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal

import hu.bme.mit.semantifyr.backends.uppaal.execution.ShellBasedUppaalExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

class UppaalExecutorProbeTest {

    @Test
    fun `the probe finds a verifyta on the path`() {
        val directories = System.getenv("PATH").orEmpty().split(File.pathSeparator)
        val onPath = directories.any {
            File(it, "verifyta").canExecute() || File(it, "verifyta.exe").canExecute()
        }
        assumeTrue(onPath, "verifyta is not on PATH")

        assertThat(ShellBasedUppaalExecutor().isAvailable()).isTrue
    }
}
