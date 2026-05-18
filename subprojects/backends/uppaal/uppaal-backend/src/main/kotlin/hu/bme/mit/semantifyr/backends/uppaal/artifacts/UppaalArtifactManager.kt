/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.artifacts

import java.io.File
import java.nio.file.Path

class UppaalArtifactManager(
    private val rootDirectory: Path,
) {
    val modelFile: File
        get() = rootDirectory.resolve("model.xml").toFile()

    val queryFile: File
        get() = rootDirectory.resolve("model.q").toFile()

    val logFile: File
        get() = rootDirectory.resolve("verifyta.out").toFile()

    val errorFile: File
        get() = rootDirectory.resolve("verifyta.err").toFile()
}
