/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.wrapper

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

        assertThat(compiled.readText()).contains("class ${CASE}Trace")
    }

    @Test
    fun `the bundled trace command generates the trace of the crossroads reference witness`() {
        val generated = SysMLv2Compiler(temporaryDirectory.resolve("cache")).trace(crossroadsWorkspace(), TRACE_JSON, CASE, temporaryDirectory.resolve("out").resolve("trace.sysml"))

        assertThat(generated.readText()).contains("part def ${CASE}Trace :> CrossroadSystem")
    }

    @Test
    fun `a failed trace generation writes no trace`() {
        val output = temporaryDirectory.resolve("out").resolve("trace.sysml")

        assertThatThrownBy {
            SysMLv2Compiler(temporaryDirectory.resolve("cache")).trace(crossroadsWorkspace(), TRACE_JSON, "NoSuchCase", output)
        }.isInstanceOf(IllegalStateException::class.java).hasMessageContaining("No verification case is named 'NoSuchCase'")
        assertThat(output.parent).isEmptyDirectory()
    }

    private fun crossroadsWorkspace(): Path {
        val workspace = temporaryDirectory.resolve("workspace").createDirectories()
        MODELS.resolve("examples").resolve(VERIFICATION).copyTo(workspace.resolve(VERIFICATION))
        return MODELS.resolve("examples").resolve(MODEL).copyTo(workspace.resolve(MODEL))
    }

    companion object {
        private val MODELS = Path("..", "models")
        private const val MODEL = "crossroads.sysml"
        private const val VERIFICATION = "Verification.sysml"
        private const val TRACE = "crossroads-green.trace.sysml"
        private val TRACE_JSON = MODELS.resolve("etc").resolve("crossroads-green.trace.json")
        private const val CASE = "TrafficLightABehaviorNormalGreenUnreachableViolated"
    }
}
