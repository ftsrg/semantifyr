/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries

@Singleton
class WorkspaceSweeper @Inject constructor(
    private val config: BackendConfig,
) {

    private val logger by loggerFactory()

    fun sweep() {
        val sessionsRoot = config.sessionManager.rootWorkPath.resolve("sessions")
        if (!Files.isDirectory(sessionsRoot)) {
            return
        }
        val entries = sessionsRoot.listDirectoryEntries()
        if (entries.isEmpty()) {
            return
        }
        logger.info { "Sweeping ${entries.size} leftover session workspace(s) under $sessionsRoot" }
        for (entry in entries) {
            deleteRecursively(entry)
        }
    }

    private fun deleteRecursively(path: Path) {
        try {
            path.toFile().deleteRecursively().also { ok ->
                if (!ok) {
                    logger.warn { "Failed to fully delete leftover workspace $path" }
                }
            }
        } catch (e: Exception) {
            logger.warn { "Failed to delete leftover workspace $path: $e" }
        }
    }
}
