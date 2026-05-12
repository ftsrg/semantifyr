/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.testing

import com.google.inject.Binder
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Module
import com.google.inject.util.Modules
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.BackendModule
import hu.bme.mit.semantifyr.live.backend.lsp.service.SharedExecutorProvider
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionManager
import hu.bme.mit.semantifyr.live.backend.server.AdminHandler
import hu.bme.mit.semantifyr.live.backend.server.ApiRoutesHandler
import hu.bme.mit.semantifyr.live.backend.server.WebSocketHandler
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.websocket.WebSockets as ServerWebSockets

fun testInjector(
    config: BackendConfig = BackendConfig(),
    bindings: Binder.() -> Unit = {},
): Injector {
    val overrideModule = Module {
        it.bindings()
    }
    return Guice.createInjector(Modules.override(BackendModule(config)).with(overrideModule))
}

inline fun <reified T : Any> Injector.handler(): T {
    return getInstance(T::class.java)
}

fun ApplicationTestBuilder.installSemantifyrApp(
    contentNegotiation: Boolean = true,
    webSockets: Boolean = false,
    configure: Application.() -> Unit,
) {
    install(
        createApplicationPlugin("semantifyr-live-test") {
            if (contentNegotiation) {
                application.install(ContentNegotiation) { json() }
            }
            if (webSockets) {
                application.install(ServerWebSockets)
            }
            application.configure()
        },
    )
}

fun ApplicationTestBuilder.jsonClient(webSockets: Boolean = false) = createClient {
    install(ClientContentNegotiation) { json() }
    if (webSockets) {
        install(ClientWebSockets)
    }
}

fun Application.installSemantifyrLiveBackend(injector: Injector) {
    with(injector.getInstance(ApiRoutesHandler::class.java)) {
        configure()
    }
    with(injector.getInstance(AdminHandler::class.java)) {
        configure()
    }
    with(injector.getInstance(WebSocketHandler::class.java)) {
        configure()
    }
}

suspend fun withRealServer(
    config: BackendConfig,
    block: suspend (HttpClient, Int) -> Unit,
) {
    val injector = Guice.createInjector(BackendModule(config))
    val server = embeddedServer(Netty, port = 0) {
        install(ContentNegotiation) {
            json()
        }
        install(ServerWebSockets)
        installSemantifyrLiveBackend(injector)
    }.start(wait = false)
    try {
        val port = server.engine.resolvedConnectors().first().port
        HttpClient(CIO) {
            install(ClientContentNegotiation) {
                json()
            }
            install(ClientWebSockets)
            engine {
                maxConnectionsCount = 4096
                endpoint.maxConnectionsPerRoute = 4096
            }
        }.use {
            block(it, port)
        }
    } finally {
        server.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
        try {
            injector.getInstance(SessionManager::class.java).close()
        } catch (_: Throwable) {
        }
        try {
            injector.getInstance(SharedExecutorProvider::class.java).close()
        } catch (_: Throwable) {
        }
    }
}
