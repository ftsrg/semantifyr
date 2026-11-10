/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.execution

import hu.bme.mit.semantifyr.backends.spin.SpinExecutionSpecification
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class ShellBasedSpinExecutorTest {

    private val executor = ShellBasedSpinExecutor()

    @Test
    fun `a search keeps statement merging and the depth bound goes to pan`() {
        val specification = SpinExecutionSpecification(
            workingDirectory = File("."),
            modelFileName = "model.pml",
            extraArguments = listOf("-DSAFETY"),
            maxSearchDepth = 20000,
        )

        assertThat(executor.searchArguments(specification)).containsExactly("-search", "-m20000", "-DSAFETY", "model.pml")
    }

    @Test
    fun `the state vector size goes to the compiler`() {
        val specification = SpinExecutionSpecification(
            workingDirectory = File("."),
            modelFileName = "model.pml",
            extraArguments = listOf("-DSAFETY"),
            vectorSize = 2048,
        )

        assertThat(executor.searchArguments(specification)).containsExactly("-search", "-DVECTORSZ=2048", "-DSAFETY", "model.pml")
    }

    @Test
    fun `a search without statement merging passes -o3 before -search`() {
        val specification = SpinExecutionSpecification(
            workingDirectory = File("."),
            modelFileName = "model.pml",
            extraArguments = listOf("-DSAFETY"),
            statementMerging = false,
        )

        assertThat(executor.searchArguments(specification)).containsExactly("-o3", "-search", "-DSAFETY", "model.pml")
    }

    @Test
    fun `spin without its C compiler is not available`() {
        assertThat(ShellBasedSpinExecutor(compilerName = "semantifyr-missing-compiler").isAvailable()).isFalse
    }
}
