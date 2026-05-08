/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp

import com.google.gson.GsonBuilder
import hu.bme.mit.semantifyr.live.backend.lsp.adapters.DurationGsonAdapter
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.registerSemantifyrLiveMethods
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import java.lang.reflect.Type
import kotlin.time.Duration

class LspMessageHandlerBuilder {

    private val methods = mutableMapOf<String, JsonRpcMethod>()
    private val gsonConfigurers = mutableListOf<(GsonBuilder) -> Unit>()

    fun addRequest(
        method: String,
        resultType: Type,
        vararg paramTypes: Type,
    ) = apply {
        methods[method] = JsonRpcMethod.request(method, resultType, *paramTypes)
    }

    fun addNotification(method: String, vararg paramTypes: Type) = apply {
        methods[method] = JsonRpcMethod.notification(method, *paramTypes)
    }

    fun configureGson(block: (GsonBuilder) -> Unit) = apply {
        gsonConfigurers += block
    }

    fun build(): MessageJsonHandler {
        return MessageJsonHandler(methods.toMap()) { gson ->
            gsonConfigurers.forEach {
                it.invoke(gson)
            }
        }
    }
}

fun createLspMessageHandler(): MessageJsonHandler {
    return LspMessageHandlerBuilder()
        .registerStandardLspMethods()
        .registerSemantifyrLiveMethods()
        .configureGson {
            it.registerTypeAdapter(Duration::class.java, DurationGsonAdapter().nullSafe())
        }.build()
}

private fun LspMessageHandlerBuilder.registerStandardLspMethods(): LspMessageHandlerBuilder {
    return addNotification("textDocument/didOpen", DidOpenTextDocumentParams::class.java)
        .addNotification("textDocument/didChange", DidChangeTextDocumentParams::class.java)
        .addRequest("workspace/executeCommand", Any::class.java, ExecuteCommandParams::class.java)
}
