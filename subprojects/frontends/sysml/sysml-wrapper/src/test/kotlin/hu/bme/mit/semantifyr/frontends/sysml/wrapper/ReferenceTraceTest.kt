/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.wrapper

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.readText

class ReferenceTraceTest {

    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `the crossroads reference trace validates against the crossroads model`() {
        val workspace = temporaryDirectory.resolve("workspace").createDirectories()
        MODELS.resolve("examples").resolve(MODEL).copyTo(workspace.resolve(MODEL))
        val trace = MODELS.resolve("etc").resolve(TRACE).copyTo(workspace.resolve(TRACE))

        val compiled = SysMLv2Compiler(temporaryDirectory.resolve("cache")).compile(trace, temporaryDirectory.resolve("trace.oxsts"))

        assertThat(compiled.readText()).contains("class TrafficLightABehaviorNormalGreenUnreachableViolatedTrace")
    }

    companion object {
        private val MODELS = Path("..", "models")
        private const val MODEL = "crossroads.sysml"
        private const val TRACE = "crossroads-green.trace.sysml"
    }
}
