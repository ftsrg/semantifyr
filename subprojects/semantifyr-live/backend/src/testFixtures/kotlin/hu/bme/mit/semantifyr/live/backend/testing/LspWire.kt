/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.testing

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object LspWire {

    private val initializeCapabilities: JsonObject = buildJsonObject {
        put(
            "workspace",
            buildJsonObject {
                put(
                    "executeCommand",
                    buildJsonObject {
                        put("dynamicRegistration", JsonPrimitive(true))
                    },
                )
            },
        )
        put(
            "window",
            buildJsonObject {
                put("workDoneProgress", JsonPrimitive(true))
            },
        )
    }

    fun request(
        id: Int,
        method: String,
        params: JsonElement = JsonObject(emptyMap()),
    ): String {
        return buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("id", JsonPrimitive(id))
            put("method", JsonPrimitive(method))
            put("params", params)
        }.toString()
    }

    fun notification(method: String, params: JsonElement = JsonObject(emptyMap())): String {
        return buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("method", JsonPrimitive(method))
            put("params", params)
        }.toString()
    }

    fun initializeRequest(id: Int = 1): String {
        return request(
            id = id,
            method = "initialize",
            params = buildJsonObject {
                put("processId", JsonPrimitive(null as String?))
                put("rootUri", JsonPrimitive("file:///workspace/"))
                put("capabilities", initializeCapabilities)
            },
        )
    }

    fun initializedNotification(): String {
        return notification(method = "initialized")
    }

    fun didOpenNotification(
        uri: String,
        languageId: String,
        text: String,
        version: Int = 1,
    ): String {
        return notification(
            method = "textDocument/didOpen",
            params = buildJsonObject {
                put(
                    "textDocument",
                    buildJsonObject {
                        put("uri", JsonPrimitive(uri))
                        put("languageId", JsonPrimitive(languageId))
                        put("version", JsonPrimitive(version))
                        put("text", JsonPrimitive(text))
                    },
                )
            },
        )
    }

    fun didChangeNotification(
        uri: String,
        version: Int,
        text: String,
    ): String {
        return notification(
            method = "textDocument/didChange",
            params = buildJsonObject {
                put(
                    "textDocument",
                    buildJsonObject {
                        put("uri", JsonPrimitive(uri))
                        put("version", JsonPrimitive(version))
                    },
                )
                put(
                    "contentChanges",
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("text", JsonPrimitive(text))
                            },
                        ),
                    ),
                )
            },
        )
    }

    fun executeCommandRequest(
        id: Int,
        command: String,
        arguments: List<JsonElement> = emptyList(),
    ): String {
        return request(
            id = id,
            method = "workspace/executeCommand",
            params = buildJsonObject {
                put("command", JsonPrimitive(command))
                put("arguments", JsonArray(arguments))
            },
        )
    }

    fun verifyCommandArgument(
        uri: String,
        range: JsonElement,
        portfolio: String,
        caseLabel: String,
        requestId: String? = null,
    ): JsonObject {
        return buildJsonObject {
            put("uri", JsonPrimitive(uri))
            put("range", range)
            put("portfolio", JsonPrimitive(portfolio))
            put("caseLabel", JsonPrimitive(caseLabel))
            if (requestId != null) {
                put("requestId", JsonPrimitive(requestId))
            }
        }
    }

    fun semanticTokensFullRequest(id: Int, uri: String): String {
        return request(
            id = id,
            method = "textDocument/semanticTokens/full",
            params = buildJsonObject {
                put(
                    "textDocument",
                    buildJsonObject {
                        put("uri", JsonPrimitive(uri))
                    },
                )
            },
        )
    }
}

private fun JsonObject.lspMethod(): String? {
    return this["method"]?.jsonPrimitive?.contentOrNull
}

private fun JsonObject.publishDiagnosticsUri(): String? {
    if (lspMethod() != "textDocument/publishDiagnostics") {
        return null
    }
    return this["params"]?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull
}

suspend fun DefaultClientWebSocketSession.ackIfRequest(message: JsonObject) {
    val method = message.lspMethod() ?: return
    val id = message["id"] ?: return
    val ack = buildJsonObject {
        put("jsonrpc", JsonPrimitive("2.0"))
        put("id", id)
        put("result", JsonNull)
    }
    send(Frame.Text(ack.toString()))
}

suspend fun DefaultClientWebSocketSession.awaitResponseFor(
    id: Int,
    timeout: Duration = 30.seconds,
): JsonObject {
    return withTimeout(timeout) {
        while (true) {
            val frame = incoming.receive() as? Frame.Text ?: continue
            val obj = parseToJsonElement(frame.readText()).jsonObject
            if (obj["id"]?.jsonPrimitive?.int == id) {
                return@withTimeout obj
            }
            ackIfRequest(obj)
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }
}

suspend fun DefaultClientWebSocketSession.awaitPublishDiagnostics(
    uri: String,
    timeout: Duration = 30.seconds,
) {
    withTimeout(timeout) {
        while (true) {
            val frame = incoming.receive() as? Frame.Text ?: continue
            val obj = parseToJsonElement(frame.readText()).jsonObject
            if (obj.publishDiagnosticsUri() == uri) {
                return@withTimeout
            }
            ackIfRequest(obj)
        }
    }
}

suspend fun DefaultClientWebSocketSession.awaitResponseCollectingNotifications(
    id: Int,
    method: String,
    timeout: Duration = 30.seconds,
): Pair<JsonObject, List<JsonObject>> {
    val collected = mutableListOf<JsonObject>()
    val response = withTimeout(timeout) {
        while (true) {
            val frame = incoming.receive() as? Frame.Text ?: continue
            val obj = parseToJsonElement(frame.readText()).jsonObject
            if (obj["id"]?.jsonPrimitive?.int == id && obj.lspMethod() == null) {
                return@withTimeout obj
            }
            if (obj.lspMethod() == method) {
                collected += obj
            }
            ackIfRequest(obj)
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }
    return response to collected
}
