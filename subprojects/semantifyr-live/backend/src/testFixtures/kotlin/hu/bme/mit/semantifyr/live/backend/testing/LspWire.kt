/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.testing

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import hu.bme.mit.semantifyr.live.backend.lsp.transport.createLspMessageJsonHandler
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.ExecuteCommandCapabilities
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WindowClientCapabilities
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// Built from the same factory the live-backend's WebSocket connector uses, so the test side
// decodes through identical Gson configuration (DurationTypeAdapter, VerificationKindTypeAdapter,
// lsp4j's Either / Enum type adapters).
private val gson = createLspMessageJsonHandler().gson

object LspWire {

    private data class JsonRpcRequest(
        val jsonrpc: String = "2.0",
        val id: Int,
        val method: String,
        val params: Any,
    )

    private data class JsonRpcNotification(
        val jsonrpc: String = "2.0",
        val method: String,
        val params: Any,
    )

    fun request(id: Int, method: String, params: Any = emptyMap<String, Any>()): String {
        return gson.toJson(JsonRpcRequest(id = id, method = method, params = params))
    }

    fun notification(method: String, params: Any = emptyMap<String, Any>()): String {
        return gson.toJson(JsonRpcNotification(method = method, params = params))
    }

    fun initializeRequest(id: Int = 1): String {
        val capabilities = ClientCapabilities().apply {
            workspace = WorkspaceClientCapabilities().apply {
                executeCommand = ExecuteCommandCapabilities().apply { dynamicRegistration = true }
            }
            window = WindowClientCapabilities().apply { workDoneProgress = true }
        }
        return request(
            id,
            "initialize",
            InitializeParams().apply {
                rootUri = "file:///workspace/"
                this.capabilities = capabilities
            },
        )
    }

    fun initializedNotification(): String {
        return notification("initialized", InitializedParams())
    }

    fun didOpenNotification(uri: String, languageId: String, text: String, version: Int = 1): String {
        return notification(
            "textDocument/didOpen",
            DidOpenTextDocumentParams(TextDocumentItem(uri, languageId, version, text)),
        )
    }

    fun didChangeNotification(uri: String, version: Int, text: String): String {
        return notification(
            "textDocument/didChange",
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(uri, version),
                listOf(TextDocumentContentChangeEvent(text)),
            ),
        )
    }

    fun executeCommandRequest(id: Int, command: String, arguments: List<Any> = emptyList()): String {
        return request(id, "workspace/executeCommand", ExecuteCommandParams(command, arguments))
    }

    fun semanticTokensFullRequest(id: Int, uri: String): String {
        return request(id, "textDocument/semanticTokens/full", SemanticTokensParams(TextDocumentIdentifier(uri)))
    }

    fun range(
        startLine: Int = 0,
        startCharacter: Int = 0,
        endLine: Int = 0,
        endCharacter: Int = 0,
    ): Range {
        return Range(Position(startLine, startCharacter), Position(endLine, endCharacter))
    }

}

fun <T> JsonObject.resultAs(type: Class<T>): T {
    val result = get("result") ?: error("response has no result: $this")
    return gson.fromJson(result, type)
}

fun <T> JsonObject.paramsAs(type: Class<T>): T {
    val params = get("params") ?: error("notification has no params: $this")
    return gson.fromJson(params, type)
}

fun <T> JsonObject.errorAs(type: Class<T>): T {
    val error = get("error") ?: error("response has no error: $this")
    return gson.fromJson(error, type)
}

private data class JsonRpcAck(
    val jsonrpc: String = "2.0",
    val id: JsonElement,
    val result: JsonElement = JsonNull.INSTANCE,
)

private fun JsonObject.lspMethod(): String? {
    val method = get("method") ?: return null
    if (!method.isJsonPrimitive) return null
    return method.asJsonPrimitive.takeIf { it.isString }?.asString
}

private fun JsonObject.publishDiagnosticsUri(): String? {
    if (lspMethod() != "textDocument/publishDiagnostics") {
        return null
    }
    val params = get("params") as? JsonObject ?: return null
    val uri = params.get("uri") ?: return null
    return uri.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString
}

private fun JsonObject.idAsInt(): Int? {
    val id = get("id") ?: return null
    if (!id.isJsonPrimitive) return null
    val primitive = id.asJsonPrimitive
    return if (primitive.isNumber) primitive.asInt else null
}

suspend fun DefaultClientWebSocketSession.ackIfRequest(message: JsonObject) {
    message.lspMethod() ?: return
    val id = message.get("id") ?: return
    send(Frame.Text(gson.toJson(JsonRpcAck(id = id))))
}

suspend fun DefaultClientWebSocketSession.awaitResponseFor(
    id: Int,
    timeout: Duration = 30.seconds,
): JsonObject {
    return withTimeout(timeout) {
        while (true) {
            val frame = incoming.receive() as? Frame.Text ?: continue
            val obj = gson.fromJson(frame.readText(), JsonObject::class.java)
            if (obj.idAsInt() == id) {
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
            val obj = gson.fromJson(frame.readText(), JsonObject::class.java)
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
            val obj = gson.fromJson(frame.readText(), JsonObject::class.java)
            if (obj.idAsInt() == id && obj.lspMethod() == null) {
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
