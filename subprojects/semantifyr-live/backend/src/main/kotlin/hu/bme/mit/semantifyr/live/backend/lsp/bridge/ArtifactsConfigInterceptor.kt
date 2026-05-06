/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.bridge

import com.google.gson.JsonObject
import com.google.inject.Inject
import hu.bme.mit.semantifyr.live.backend.session.SessionContext
import hu.bme.mit.semantifyr.live.backend.session.SessionScoped
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories

@SessionScoped
class ArtifactsConfigInterceptor @Inject constructor(
    private val context: SessionContext,
) : LspMessageInterceptor {

    private val logger by loggerFactory()
    private var configSent = false

    override suspend fun interceptClientMessage(
        raw: String,
        message: Message,
        bridge: LspBridge,
    ): Boolean {
        if (configSent) {
            return false
        }
        if (message !is NotificationMessage || message.method != "initialized") {
            return false
        }
        configSent = true
        val artifactsDir = context.workingDirectoryPath.resolve("artifacts")
        artifactsDir.createDirectories()

        val notification = NotificationMessage().apply {
            method = "workspace/didChangeConfiguration"
            params = buildSettingsParams(artifactsDir.absolutePathString())
        }
        logger.info { "Routing LSP artifacts directory to $artifactsDir" }
        bridge.sendToLspServer(notification)
        return false
    }

    private fun buildSettingsParams(artifactsPath: String): JsonObject {
        val semantifyr = JsonObject().apply {
            addProperty("artifacts.location", "directory")
            addProperty("artifacts.directory", artifactsPath)
        }
        val settings = JsonObject().apply {
            add("semantifyr", semantifyr)
        }
        val outer = JsonObject().apply {
            add("settings", settings)
        }
        return outer
    }
}
