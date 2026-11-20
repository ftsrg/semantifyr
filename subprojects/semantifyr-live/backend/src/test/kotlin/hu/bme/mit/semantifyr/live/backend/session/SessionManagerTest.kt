/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Injector
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.Flavor
import hu.bme.mit.semantifyr.live.backend.SessionManagerConfig
import hu.bme.mit.semantifyr.live.backend.exceptions.SessionLimitReachedException
import hu.bme.mit.semantifyr.live.backend.lsp.document.SessionDocumentManager
import hu.bme.mit.semantifyr.live.backend.lsp.language.LanguageServiceRegistry
import hu.bme.mit.semantifyr.live.backend.lsp.language.LanguageServices
import hu.bme.mit.semantifyr.live.backend.lsp.service.SessionLanguageServer
import hu.bme.mit.semantifyr.live.backend.lsp.service.SharedExecutorProvider
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionContext
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionKind
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionManager
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionScope
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionScoped
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionVerificationManager
import hu.bme.mit.semantifyr.live.backend.lsp.session.seededKeyProvider
import hu.bme.mit.semantifyr.live.backend.testing.testFlavor
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.Frame
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.jsonrpc.Endpoint
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

class SessionManagerTest {

    @TempDir
    lateinit var rootWorkDirectory: Path

    private val sharedExecutorProvider = SharedExecutorProvider()

    private val meterRegistry = SimpleMeterRegistry()

    private val connected = Channel<Unit>(Channel.UNLIMITED)

    private val sessionLanguageServer = mock<SessionLanguageServer> {
        on { toEndpoint() } doReturn mock<Endpoint>()
        on { connect(any()) } doAnswer {
            connected.trySend(Unit)
            Unit
        }
    }

    private val sessionInjector = Guice.createInjector(
        object : AbstractModule() {
            override fun configure() {
                bindScope(SessionScoped::class.java, SessionScope)
                bind(SessionContext::class.java).toProvider(seededKeyProvider()).`in`(SessionScoped::class.java)
                bind(SharedExecutorProvider::class.java).toInstance(sharedExecutorProvider)
                bind(SessionDocumentManager::class.java).toInstance(mock())
                bind(SessionLanguageServer::class.java).toInstance(sessionLanguageServer)
                bind(SessionVerificationManager::class.java).toInstance(
                    mock {
                        on { active() } doReturn emptyList()
                    },
                )
            }
        },
    )

    private val languageServiceRegistry = object : LanguageServiceRegistry {
        override fun forFlavor(flavor: Flavor): LanguageServices {
            error("the session manager reaches a flavor's services through its injector")
        }

        override fun injectorFor(flavor: Flavor): Injector {
            return sessionInjector
        }
    }

    private class Client {
        val incoming = Channel<Frame>()
        val session = mock<WebSocketServerSession> {
            on { incoming } doReturn incoming
        }
    }

    @AfterEach
    fun closeExecutor() {
        sharedExecutorProvider.close()
    }

    private fun sessionManager(maxSessionsGlobal: Int = 4): SessionManager {
        val config = BackendConfig(
            sessionManager = SessionManagerConfig(
                maxSessionsGlobal = maxSessionsGlobal,
                rootWorkDirectory = rootWorkDirectory.toString(),
            ),
        )
        return SessionManager(config, languageServiceRegistry, meterRegistry)
    }

    private fun start(manager: SessionManager, client: Client): Deferred<Unit> {
        return CoroutineScope(Dispatchers.Default).async {
            manager.runSession(client.session, "127.0.0.1", testFlavor(), SessionKind.Embed, "oxsts")
        }
    }

    @Test
    suspend fun `a running session is listed with its working directory, and its end frees the slot and removes the directory`() {
        val manager = sessionManager()
        val client = Client()
        withTimeout(10.seconds) {
            val session = start(manager, client)
            connected.receive()

            val info = manager.getSessionInfos().single()
            assertThat(info.flavorId).isEqualTo("oxsts")
            assertThat(info.kind).isEqualTo("embed")
            assertThat(manager.activeSessionsCount).isEqualTo(1)
            val workingDirectory = Path.of(info.workingDirectory)
            assertThat(workingDirectory).isDirectory().startsWith(rootWorkDirectory)

            client.incoming.close()
            session.await()

            assertThat(manager.getSessionInfos()).isEmpty()
            assertThat(manager.activeSessionsCount).isEqualTo(0)
            assertThat(Files.exists(workingDirectory)).isFalse()
        }
    }

    @Test
    suspend fun `a session past the global limit is refused and counted as rejected`() {
        val manager = sessionManager(maxSessionsGlobal = 1)
        val first = Client()
        withTimeout(10.seconds) {
            val running = start(manager, first)
            connected.receive()

            val refusal = try {
                manager.runSession(Client().session, "127.0.0.1", testFlavor(), SessionKind.Full, "oxsts")
                null
            } catch (e: SessionLimitReachedException) {
                e
            }

            assertThat(refusal).isNotNull
            assertThat(meterRegistry.get("semantifyr.live.sessions.rejected").counter().count()).isEqualTo(1.0)
            assertThat(meterRegistry.get("semantifyr.live.sessions.active").gauge().value()).isEqualTo(1.0)
            assertThat(meterRegistry.get("semantifyr.live.sessions.limit").gauge().value()).isEqualTo(1.0)

            first.incoming.close()
            running.await()
        }
    }

    @Test
    suspend fun `cancelling a session by its id ends it`() {
        val manager = sessionManager()
        withTimeout(10.seconds) {
            val session = start(manager, Client())
            connected.receive()

            assertThat(manager.cancelSession(manager.getSessionInfos().single().sessionId)).isTrue()
            session.join()

            assertThat(session.isCancelled).isTrue()
            assertThat(manager.activeSessionsCount).isEqualTo(0)
        }
    }

    @Test
    fun `cancelling an unknown session or verification finds nothing`() {
        val manager = sessionManager()
        assertThat(manager.cancelSession("nonexistent")).isFalse()
        assertThat(manager.cancelVerification("request-1")).isFalse()
    }
}
