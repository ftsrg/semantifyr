/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.integration

import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.ServerConfig
import hu.bme.mit.semantifyr.live.backend.SessionManagerConfig
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

object IntegrationTestSupport {

    const val ADMIN_PASSWORD = "integration-admin-password"

    val semanticLibrariesDirectory = systemPropertyPath("semantifyr.live.semanticLibraries")
    val oxstsTestModelsDirectory = systemPropertyPath("semantifyr.live.oxstsTestModels")
    val gammaTestModelsDirectory = systemPropertyPath("semantifyr.live.gammaTestModels")
    val gammaLibraryModelsDirectory = systemPropertyPath("semantifyr.live.gammaLibraryModels")
    val sysmlLibraryModelsDirectory = systemPropertyPath("semantifyr.live.sysmlLibraryModels")

    private fun systemPropertyPath(name: String): Path? {
        return System.getProperty(name)?.let { Path.of(it) }
    }

    fun assumeStaged() {
        assumeTrue(semanticLibrariesDirectory != null, "semantifyr.live.semanticLibraries system property not set")
    }

    fun config(rootWorkDirectory: Path, maxSessionsGlobal: Int = 4): BackendConfig {
        val libraries = checkNotNull(semanticLibrariesDirectory) {
            "semantifyr.live.semanticLibraries must point to the staged semantic libraries directory"
        }
        return BackendConfig(
            server = ServerConfig(adminPassword = ADMIN_PASSWORD, wsHandshakesPerPeriod = 100_000),
            sessionManager = SessionManagerConfig(
                rootWorkDirectory = rootWorkDirectory.toString(),
                semanticLibrariesDirectory = libraries.toString(),
                maxSessionsGlobal = maxSessionsGlobal,
            ),
        )
    }

    fun stagedModel(
        directory: Path?,
        propertyName: String,
        filename: String,
    ): String {
        assumeTrue(directory != null, "$propertyName system property not set")
        val source = checkNotNull(directory).resolve(filename)
        assumeTrue(Files.exists(source), "$filename not found at $source")
        return Files.readString(source)
    }
}
