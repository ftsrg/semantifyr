/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.artifacts

import java.io.File
import java.nio.file.Path

class NuxmvArtifactManager(
    private val rootDirectory: Path,
) {
    val modelFile: File
        get() = rootDirectory.resolve("model.smv").toFile()

    val commandFile: File
        get() = rootDirectory.resolve("commands.cmd").toFile()

    val logFile: File
        get() = rootDirectory.resolve("nuxmv.out").toFile()

    val errorFile: File
        get() = rootDirectory.resolve("nuxmv.err").toFile()
}
