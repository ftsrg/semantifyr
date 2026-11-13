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
        CORPUS.resolve(MODEL).copyTo(workspace.resolve(MODEL))
        CORPUS.resolve(VERIFICATION).copyTo(workspace.resolve(VERIFICATION))
        val trace = MODELS.resolve("etc").resolve(TRACE).copyTo(workspace.resolve(TRACE))

        SysMLv2Compiler(temporaryDirectory.resolve("cache")).validate(trace)
    }

    // the fork's golden test compares the text whole, the command writes it only when SysIDE validates it
    @Test
    fun `the bundled trace command generates a valid trace of the reference witness`() {
        val generated = generateTrace(CASE, temporaryDirectory.resolve("out").resolve("trace.sysml"))

        assertThat(generated.readText()).contains("package ${CASE}Trace {")
    }

    @Test
    fun `a failed trace generation writes no trace`() {
        val output = temporaryDirectory.resolve("out").resolve("trace.sysml")

        assertThatThrownBy {
            generateTrace("NoSuchCase", output)
        }.isInstanceOf(IllegalStateException::class.java).hasMessageContaining("no verification case is named 'NoSuchCase'")
        assertThat(output.parent).isEmptyDirectory()
    }

    private fun generateTrace(caseName: String, output: Path): Path {
        val compiler = SysMLv2Compiler(temporaryDirectory.resolve("cache"))
        val model = crossroadsWorkspace()
        val compiled = compiler.compile(model, temporaryDirectory.resolve("compiled").resolve("crossroads.oxsts"))
        output.parent.createDirectories()
        return compiler.trace(model, WITNESS_JSON, TRACE_JSON, nameMapPath(compiled), caseName, output)
    }

    private fun crossroadsWorkspace(): Path {
        val workspace = temporaryDirectory.resolve("workspace").createDirectories()
        CORPUS.resolve(VERIFICATION).copyTo(workspace.resolve(VERIFICATION))
        return CORPUS.resolve(MODEL).copyTo(workspace.resolve(MODEL))
    }

    companion object {
        private val MODELS = Path("..", "models")

        // the reference case is a further case of crossroads, which the build appends to the model
        private val CORPUS = Path("build", "corpus")
        private const val MODEL = "crossroads.sysml"
        private const val VERIFICATION = "Verification.sysml"
        private const val TRACE = "crossroads-green.trace.sysml"
        private val TRACE_JSON = MODELS.resolve("etc").resolve("crossroads-green.trace.json")
        private val WITNESS_JSON = MODELS.resolve("etc").resolve("crossroads-green.witness.json")
        private const val CASE = "TrafficLightABehaviorNormalGreenUnreachableViolated"
    }
}
