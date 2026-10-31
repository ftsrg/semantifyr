/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

private const val CAPTURE_TOOL_OUTPUT_VARIABLE = "SEMANTIFYR_CAPTURE_TOOL_OUTPUT"

private const val WORKING_DIRECTORY_PLACEHOLDER = "<working-directory>"

class CapturedToolOutput(
    engine: String,
    private val case: String,
) {
    private val resourceDirectory = "captured/$engine/$case"

    private val isCapturing = System.getenv(CAPTURE_TOOL_OUTPUT_VARIABLE) != null

    suspend fun replay(
        step: String,
        workingDirectory: File,
        outputs: List<File>,
        runTool: suspend () -> Int,
    ): Int {
        if (isCapturing) {
            val exitCode = runTool()
            record(step, workingDirectory, outputs, exitCode)
            return exitCode
        }
        return restore(step, workingDirectory, outputs)
    }

    private fun record(
        step: String,
        workingDirectory: File,
        outputs: List<File>,
        exitCode: Int,
    ) {
        // tests run in the project directory, so this is the module's source tree
        val directory = Path("src/test/resources", resourceDirectory).createDirectories()
        directory.resolve("$step.exit").writeText("$exitCode\n")
        val produced = outputs.filter {
            it.exists() && it.length() > 0
        }
        for (output in produced) {
            val content = output.readText().replace(workingDirectory.absolutePath, WORKING_DIRECTORY_PLACEHOLDER)
            directory.resolve(output.name).writeText(content)
        }
    }

    private fun restore(
        step: String,
        workingDirectory: File,
        outputs: List<File>,
    ): Int {
        val exitCode = readResource("$step.exit")?.trim()?.toInt()
            ?: error("No captured output for $case, rerun the test with $CAPTURE_TOOL_OUTPUT_VARIABLE=1")
        for (output in outputs) {
            val content = readResource(output.name) ?: continue
            output.parentFile?.mkdirs()
            output.writeText(content.replace(WORKING_DIRECTORY_PLACEHOLDER, workingDirectory.absolutePath))
        }
        return exitCode
    }

    private fun readResource(name: String): String? {
        return javaClass.getResource("/$resourceDirectory/$name")?.readText()
    }
}
