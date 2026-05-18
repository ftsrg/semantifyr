/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.artifacts

import java.io.File
import java.nio.file.Path

class SpinArtifactManager(
    private val rootDirectory: Path,
) {
    val modelFile: File
        get() = rootDirectory.resolve("model.pml").toFile()

    val logFile: File
        get() = rootDirectory.resolve("spin.out").toFile()

    val errorFile: File
        get() = rootDirectory.resolve("spin.err").toFile()

    val trailFile: File
        get() = rootDirectory.resolve("model.pml.trail").toFile()

    val replayLogFile: File
        get() = rootDirectory.resolve("spin-replay.out").toFile()
}
