/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.bridge

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.inject.Inject
import hu.bme.mit.semantifyr.live.backend.server.ActiveVerificationInfo
import hu.bme.mit.semantifyr.live.backend.session.SessionScoped
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage

const val INFLIGHT_LIST_METHOD = "semantifyr/live/inflight/list"
const val INFLIGHT_CANCEL_METHOD = "semantifyr/live/inflight/cancel"
const val INFLIGHT_CANCEL_ALL_METHOD = "semantifyr/live/inflight/cancelAll"
const val INFLIGHT_CHANGED_NOTIFICATION = "semantifyr/live/inflight/changed"

private const val LIVE_PREFIX = "semantifyr/live/"

interface SessionControlManager {
    fun listInFlight(): List<ActiveVerificationInfo>
    suspend fun cancelInFlight(requestId: String): Boolean
    suspend fun cancelAllInFlight(): Int
}

@SessionScoped
class SessionControlInterceptor @Inject constructor(
    private val sessionControl: SessionControlManager,
) : LspMessageInterceptor {

    private val logger by loggerFactory()

    override suspend fun interceptClientMessage(
        raw: String,
        message: Message,
        bridge: LspBridge,
    ): Boolean {
        if (message !is RequestMessage || !message.method.startsWith(LIVE_PREFIX)) {
            return false
        }

        logger.info { "Intercepted ${message.method} (requestId=${message.id})" }

        val response = when (message.method) {
            INFLIGHT_LIST_METHOD -> okResponse(message, encodeInflight(sessionControl.listInFlight()))
            INFLIGHT_CANCEL_METHOD -> {
                val requestId = (message.params as? JsonObject)?.get("requestId")?.takeIf { it.isJsonPrimitive }?.asString
                if (requestId == null) {
                    errorResponse(message, "Missing requestId")
                } else {
                    okResponse(message, JsonParser.parseString(if (sessionControl.cancelInFlight(requestId)) "true" else "false"))
                }
            }
            INFLIGHT_CANCEL_ALL_METHOD -> okResponse(message, JsonParser.parseString(sessionControl.cancelAllInFlight().toString()))
            else -> {
                logger.info { "Unknown ${LIVE_PREFIX}* method: ${message.method}; replying with method-not-found" }
                errorResponse(message, "Unknown method: ${message.method}")
            }
        }

        bridge.sendToLspClient(response)

        return true
    }

    private fun okResponse(request: RequestMessage, result: Any?): ResponseMessage {
        return ResponseMessage().apply {
            rawId = request.rawId
            this.result = result
        }
    }

    private fun errorResponse(request: RequestMessage, message: String): ResponseMessage {
        return ResponseMessage().apply {
            rawId = request.rawId
            error = ResponseError(-32602, message, null)
        }
    }

    companion object {
        private val infoListJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encodeInflight(items: List<ActiveVerificationInfo>): JsonObject {
            val asString = infoListJson.encodeToString(ListSerializer(ActiveVerificationInfo.serializer()), items)
            return JsonObject().apply {
                add("inflight", JsonParser.parseString(asString))
            }
        }

        fun changedNotification(items: List<ActiveVerificationInfo>): NotificationMessage {
            return NotificationMessage().apply {
                method = INFLIGHT_CHANGED_NOTIFICATION
                params = encodeInflight(items)
            }
        }
    }
}
