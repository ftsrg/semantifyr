/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.semantics

import java.io.File

class StandaloneSysMLTransformer {

    fun transformModel(modelPath: File, outputPath: File? = null) {
        val outputFile = outputPath ?: File(modelPath.absolutePath.replace(".sysml", ".oxsts"))

        val process = ProcessBuilder("node", "build/cli/index.js", "compile", modelPath.absolutePath, "build/cli/sysml.library", "-o", outputFile.absolutePath)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            error("SysML compilation failed with exit code $exitCode:\n$output")
        }
    }

}
