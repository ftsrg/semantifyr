/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv

import hu.bme.mit.semantifyr.backends.nuxmv.execution.ShellBasedNuxmvExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

class NuxmvExecutorProbeTest {

    @Test
    fun `the probe finds a nuXmv on the path`() {
        val directories = System.getenv("PATH").orEmpty().split(File.pathSeparator)
        val onPath = directories.any {
            File(it, "nuXmv").canExecute() || File(it, "nuXmv.exe").canExecute()
        }
        assumeTrue(onPath, "nuXmv is not on PATH")

        assertThat(ShellBasedNuxmvExecutor().isAvailable()).isTrue
    }
}
