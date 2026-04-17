/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.artifacts

import com.google.inject.Singleton
import org.eclipse.emf.common.util.URI
import java.io.File
import java.nio.file.Path

@Singleton
class ThetaArtifactManager(private val rootDirectory: Path) {

    val xstsFile: File
        get() = rootDirectory.resolve("inlined.xsts").toFile()

    val xstsUri: URI
        get() = URI.createFileURI(xstsFile.absolutePath)

}
