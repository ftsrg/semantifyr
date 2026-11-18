/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin

import hu.bme.mit.semantifyr.backends.spin.execution.ShellBasedSpinExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

class SpinExecutorProbeTest {

    @Test
    fun `the probe finds a spin and its C compiler on the path`() {
        val directories = System.getenv("PATH").orEmpty().split(File.pathSeparator)
        val onPath = listOf(
            "spin",
            "gcc",
        ).all { binary ->
            directories.any {
                File(it, binary).canExecute() || File(it, "$binary.exe").canExecute()
            }
        }
        assumeTrue(onPath, "spin or gcc is not on PATH")

        assertThat(ShellBasedSpinExecutor().isAvailable()).isTrue
    }
}
