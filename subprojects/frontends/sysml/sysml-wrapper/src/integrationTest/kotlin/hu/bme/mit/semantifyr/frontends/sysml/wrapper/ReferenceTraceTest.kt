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
        RESOURCES.resolve(MODEL).copyTo(workspace.resolve(MODEL))
        RESOURCES.resolve(VERIFICATION).copyTo(workspace.resolve(VERIFICATION))
        val trace = RESOURCES.resolve(TRACE).copyTo(workspace.resolve(TRACE))

        SysMLv2Compiler(temporaryDirectory.resolve("cache")).validate(trace)
    }

    @Test
    fun `the room reference trace validates against the room model`() {
        val workspace = temporaryDirectory.resolve("workspace").createDirectories()
        RESOURCES.resolve(ROOM).copyTo(workspace.resolve(ROOM))
        val trace = RESOURCES.resolve(ROOM_TRACE).copyTo(workspace.resolve(ROOM_TRACE))

        SysMLv2Compiler(temporaryDirectory.resolve("cache")).validate(trace)
    }

    // the fork's golden test compares the text whole, the command writes it only when SysIDE validates it
    @Test
    fun `the bundled trace command generates a valid trace of the reference report`() {
        val generated = generateTrace(REPORT, temporaryDirectory.resolve("out").resolve("trace.sysml"))

        assertThat(generated.readText()).contains("package ${CASE}Trace {")
    }

    @Test
    fun `a failed trace generation writes no trace`() {
        val output = temporaryDirectory.resolve("out").resolve("trace.sysml")
        val compiler = SysMLv2Compiler(temporaryDirectory.resolve("cache"))
        val model = crossroadsWorkspace()
        val nameMap = nameMapPath(compiler.compile(model, temporaryDirectory.resolve("compiled").resolve("crossroads.semantifyr")))
        output.parent.createDirectories()

        assertThatThrownBy {
            compiler.trace(model, nameMap, nameMap, output)
        }.isInstanceOf(IllegalStateException::class.java)
        assertThat(output.parent).isEmptyDirectory()
    }

    private fun generateTrace(report: Path, output: Path): Path {
        val compiler = SysMLv2Compiler(temporaryDirectory.resolve("cache"))
        val model = crossroadsWorkspace()
        val compiled = compiler.compile(model, temporaryDirectory.resolve("compiled").resolve("crossroads.semantifyr"))
        output.parent.createDirectories()
        return compiler.trace(model, report, nameMapPath(compiled), output)
    }

    private fun crossroadsWorkspace(): Path {
        val workspace = temporaryDirectory.resolve("workspace").createDirectories()
        RESOURCES.resolve(VERIFICATION).copyTo(workspace.resolve(VERIFICATION))
        return RESOURCES.resolve(MODEL).copyTo(workspace.resolve(MODEL))
    }

    companion object {
        // the crossroads model carries its further cases, the reference case among them
        private val RESOURCES = Path("src", "integrationTest", "resources", "ReferenceTraceTest")
        private const val MODEL = "crossroads.sysml"
        private const val VERIFICATION = "Verification.sysml"
        private const val TRACE = "crossroads-green.trace.sysml"
        private val REPORT = RESOURCES.resolve("crossroads-green.report.json")
        private const val CASE = "TrafficLightABehaviorNormalGreenUnreachableViolated"
        private const val ROOM = "room.sysml"
        private const val ROOM_TRACE = "room-lamp-lit.trace.sysml"
    }
}
