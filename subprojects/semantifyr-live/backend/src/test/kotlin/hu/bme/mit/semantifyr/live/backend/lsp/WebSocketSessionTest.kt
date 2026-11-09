/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp

import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseSpecification
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.ServerConfig
import hu.bme.mit.semantifyr.live.backend.SessionManagerConfig
import hu.bme.mit.semantifyr.live.backend.data.AdminStatusResponse
import hu.bme.mit.semantifyr.live.backend.data.SessionInfo
import hu.bme.mit.semantifyr.live.backend.lsp.service.SemantifyrLiveMethods
import hu.bme.mit.semantifyr.live.backend.server.ProxyUserResolver
import hu.bme.mit.semantifyr.live.backend.testing.LspWire
import hu.bme.mit.semantifyr.live.backend.testing.awaitPublishDiagnostics
import hu.bme.mit.semantifyr.live.backend.testing.awaitResponseFor
import hu.bme.mit.semantifyr.live.backend.testing.installSemantifyrApp
import hu.bme.mit.semantifyr.live.backend.testing.installSemantifyrLiveBackend
import hu.bme.mit.semantifyr.live.backend.testing.jsonClient
import hu.bme.mit.semantifyr.live.backend.testing.paramsAs
import hu.bme.mit.semantifyr.live.backend.testing.resultAs
import hu.bme.mit.semantifyr.live.backend.testing.testInjector
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

class WebSocketSessionTest {

    private val modelWithCase = """
        package test

        @VerificationCase
        class CaseA {
            prop {
                return true
            }
        }
    """.trimIndent()

    private fun config(tmp: Path): BackendConfig {
        return BackendConfig(
            server = ServerConfig(wsHandshakesPerPeriod = 10_000),
            sessionManager = SessionManagerConfig(rootWorkDirectory = tmp.toString(), maxSessionsGlobal = 16),
        )
    }

    private suspend fun DefaultClientWebSocketSession.openWith(uri: String, text: String = modelWithCase) {
        send(Frame.Text(LspWire.initializeRequest()))
        awaitResponseFor(id = 1)
        send(Frame.Text(LspWire.initializedNotification()))
        send(Frame.Text(LspWire.didOpenNotification(uri = uri, languageId = "oxsts", text = text)))
    }

    private suspend fun DefaultClientWebSocketSession.discover(id: Int, uri: String): List<VerificationCaseSpecification> {
        send(
            Frame.Text(
                LspWire.executeCommandRequest(id = id, command = "oxsts.case.discover", arguments = listOf(uri)),
            ),
        )
        return awaitResponseFor(id = id).resultAs(Array<VerificationCaseSpecification>::class.java).toList()
    }

    @Test
    fun `initialize over websocket advertises only the flavor's commands`(@TempDir tmp: Path) = testApplication {
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(tmp)))
        }
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            val response = awaitResponseFor(id = 1).resultAs(InitializeResult::class.java)
            assertThat(response.capabilities.executeCommandProvider.commands).containsExactly(
                "oxsts.case.discover",
                "oxsts.case.verify",
                "oxsts.case.validateWitness",
                "oxsts.redefinable.redefined.navigate",
                "oxsts.redefinable.redefiners.navigate",
            )
        }
    }

    @Test
    fun `initialize advertises code actions for the oxsts flavor`(@TempDir tmp: Path) = testApplication {
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(tmp)))
        }
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            val response = awaitResponseFor(id = 1).resultAs(InitializeResult::class.java)
            assertThat(response.capabilities.codeActionProvider.left).isTrue
        }
    }

    @Test
    fun `code action over websocket adds redefine to a shadowing transition`(@TempDir tmp: Path) = testApplication {
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(tmp)))
        }
        val uri = "file:///workspace/snippet.oxsts"
        val model = """
            package test

            class Light {
                tran toggle() {
                }
            }

            class Special : Light {
                tran toggle() {
                }
            }
        """.trimIndent()
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            openWith(uri, model)
            val diagnostics = awaitPublishDiagnostics(uri).paramsAs(PublishDiagnosticsParams::class.java).diagnostics
            val shadowed = diagnostics.filter {
                it.code?.left == "OXSTS.ELEMENT_SHADOWED"
            }
            assertThat(shadowed).hasSize(1)
            val params = CodeActionParams(TextDocumentIdentifier(uri), shadowed[0].range, CodeActionContext(shadowed))
            send(Frame.Text(LspWire.request(id = 2, method = "textDocument/codeAction", params = params)))
            val codeActions = awaitResponseFor(id = 2).resultAs(Array<CodeAction>::class.java)
            assertThat(
                codeActions.map {
                    it.title
                },
            ).containsExactly("Add redefine")
            val edits = codeActions[0].edit.changes
            assertThat(edits.keys).containsExactly(uri)
            assertThat(edits.getValue(uri)).hasSize(1)
            val edit = edits.getValue(uri)[0]
            assertThat(edit.newText).isEqualTo("redefine ")
            assertThat(edit.range.start).isEqualTo(Position(8, 4))
        }
    }

    @Test
    fun `session info request is intercepted and returns metadata`(@TempDir tmp: Path) = testApplication {
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(tmp)))
        }
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            awaitResponseFor(id = 1)
            send(Frame.Text(LspWire.request(id = 2, method = SemantifyrLiveMethods.SESSION_INFO)))
            val info = awaitResponseFor(id = 2).resultAs(SessionInfo::class.java)
            assertThat(info.flavorId).isEqualTo("oxsts")
            assertThat(info.workingDirectory).contains(tmp.toString())
        }
    }

    @Test
    fun `admin status reports the live session while connected`(@TempDir tmp: Path) = testApplication {
        val signedIn = mock<ProxyUserResolver> {
            on { resolve(any(), any()) } doReturn "octocat"
        }
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(
                testInjector(config(tmp)) {
                    bind(ProxyUserResolver::class.java).toInstance(signedIn)
                },
            )
        }
        val client = jsonClient(webSockets = true)
        client.webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            awaitResponseFor(id = 1)

            val status = client.get("/api/admin/status").body<AdminStatusResponse>()
            assertThat(status.sessions).hasSize(1)
            assertThat(status.sessions[0].flavorId).isEqualTo("oxsts")
        }
    }

    @Test
    fun `discover over websocket returns the verification cases`(@TempDir tmp: Path) = testApplication {
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(tmp)))
        }
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            openWith("file:///workspace/snippet.oxsts")
            val cases = discover(id = 2, uri = "file:///workspace/snippet.oxsts")
            assertThat(cases).isNotEmpty
            assertThat(
                cases.map {
                    it.label()
                },
            ).anyMatch {
                it.contains("CaseA")
            }
        }
    }

    @Test
    fun `semanticTokens full returns a non-empty data array`(@TempDir tmp: Path) = testApplication {
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(tmp)))
        }
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            openWith("file:///workspace/snippet.oxsts")
            send(Frame.Text(LspWire.semanticTokensFullRequest(id = 2, uri = "file:///workspace/snippet.oxsts")))
            val tokens = awaitResponseFor(id = 2).resultAs(SemanticTokens::class.java)
            assertThat(tokens.data).isNotEmpty
        }
    }

    @Test
    fun `signature help over websocket shows the callee and the active parameter`(@TempDir tmp: Path) = testApplication {
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(tmp)))
        }
        val model = """
            package test

            class Light {
                var level: int := 0

                tran dim(from: int, to: int) {
                    level := to
                }

                tran {
                    inline dim(1, 0)
                }
            }
        """.trimIndent()
        val uri = "file:///workspace/snippet.oxsts"
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            val capabilities = awaitResponseFor(id = 1).resultAs(InitializeResult::class.java).capabilities
            assertThat(capabilities.signatureHelpProvider.triggerCharacters).containsExactly("(", ",")
            send(Frame.Text(LspWire.initializedNotification()))
            send(Frame.Text(LspWire.didOpenNotification(uri = uri, languageId = "oxsts", text = model)))
            val params = SignatureHelpParams(TextDocumentIdentifier(uri), Position(10, 22))
            send(Frame.Text(LspWire.request(id = 2, method = "textDocument/signatureHelp", params = params)))
            val signatureHelp = awaitResponseFor(id = 2).resultAs(SignatureHelp::class.java)
            assertThat(
                signatureHelp.signatures.map {
                    it.label
                },
            ).containsExactly("dim(from: int, to: int)")
            assertThat(signatureHelp.activeParameter).isEqualTo(1)
        }
    }

    @Test
    fun `semanticTokens stay populated when an extra OxstsStandaloneSetup is constructed at runtime`(
        @TempDir tmp: Path,
    ) = testApplication {
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(tmp)))
        }
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            openWith("file:///workspace/snippet.oxsts")

            send(Frame.Text(LspWire.semanticTokensFullRequest(id = 2, uri = "file:///workspace/snippet.oxsts")))
            val baseline = awaitResponseFor(id = 2).resultAs(SemanticTokens::class.java)
            assertThat(baseline.data)
                .withFailMessage("baseline semantic tokens were empty before any overwrite")
                .isNotEmpty

            // Regression for the live-backend bug where every Gamma verification leaked an
            // OxstsStandaloneSetup().createInjectorAndDoEMFRegistration() call out of the
            // GammaFrontend.Builder fallback. That fallback overwrote the LSP injector's
            // rich IResourceServiceProvider in the global registry with a barebones one,
            // and Xtext's SemanticTokensService.getPositions (which resolves the highlighter
            // per-resource through IResourceServiceProvider.Registry.INSTANCE) silently
            // started routing to the no-op default calculator, returning empty token data.
            // Definition / hover / formatting kept working because they read services from
            // the LSP injector directly. Restarting the backend made things work again.
            // After the fix the registered LSP provider must survive any number of extra
            // OxstsStandaloneSetup constructions in the same JVM.
            OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()

            send(Frame.Text(LspWire.semanticTokensFullRequest(id = 3, uri = "file:///workspace/snippet.oxsts")))
            val afterOverwrite = awaitResponseFor(id = 3).resultAs(SemanticTokens::class.java)
            assertThat(afterOverwrite.data)
                .withFailMessage("semantic tokens collapsed to empty after a duplicate OxstsStandaloneSetup ran")
                .isNotEmpty
        }
    }

    @Test
    fun `rapid didChange does not break a later discover`(@TempDir tmp: Path) = testApplication {
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(tmp)))
        }
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            openWith("file:///workspace/snippet.oxsts")
            repeat(20) {
                send(
                    Frame.Text(
                        LspWire.didChangeNotification(
                            uri = "file:///workspace/snippet.oxsts",
                            version = it + 2,
                            text = "$modelWithCase\n// edit $it",
                        ),
                    ),
                )
                delay(10)
            }
            assertThat(discover(id = 99, uri = "file:///workspace/snippet.oxsts")).isNotEmpty
        }
    }

    @Test
    fun `concurrent websocket sessions each discover independently`(@TempDir tmp: Path) = testApplication {
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(tmp)))
        }
        val client = jsonClient(webSockets = true)
        coroutineScope {
            val results = (1..4).map {
                async {
                    client.webSocket("/ws/lsp/oxsts") {
                        val uri = "file:///workspace/s$it.oxsts"
                        openWith(uri)
                        delay(20L * it)
                        assertThat(discover(id = 2, uri = uri)).isNotEmpty
                    }
                }
            }
            withTimeout(60.seconds) {
                results.awaitAll()
            }
        }
    }

    @Test
    fun `a session past the global limit is closed with code 4429 and counted as rejected`(@TempDir tmp: Path) = testApplication {
        val limitedConfig = config(tmp).run { copy(sessionManager = sessionManager.copy(maxSessionsGlobal = 1)) }
        val injector = testInjector(limitedConfig)
        val registry = injector.getInstance(MeterRegistry::class.java)
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(injector)
        }
        val client = jsonClient(webSockets = true)
        val firstReady = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        coroutineScope {
            val first = launch {
                client.webSocket("/ws/lsp/oxsts") {
                    send(Frame.Text(LspWire.initializeRequest()))
                    awaitResponseFor(id = 1)
                    firstReady.complete(Unit)
                    releaseFirst.await()
                }
            }
            withTimeout(30.seconds) {
                firstReady.await()
                client.webSocket("/ws/lsp/oxsts") {
                    val reason = closeReason.await()
                    assertThat(reason?.code).isEqualTo(4429.toShort())
                }
            }
            assertThat(registry.get("semantifyr.live.sessions.active").gauge().value()).isEqualTo(1.0)
            assertThat(registry.get("semantifyr.live.sessions.limit").gauge().value()).isEqualTo(1.0)
            assertThat(registry.get("semantifyr.live.sessions.rejected").counter().count()).isEqualTo(1.0)
            releaseFirst.complete(Unit)
            first.join()
        }
    }

    @Test
    fun `the per-session working directory is created during a session and removed afterwards`(
        @TempDir tmp: Path,
    ) = testApplication {
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(tmp)))
        }
        val sessionsDir = tmp.resolve("sessions")
        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            awaitResponseFor(id = 1)
            val sessionCount = Files.list(sessionsDir).use {
                it.count()
            }
            assertThat(sessionCount).isEqualTo(1L)
        }
        withTimeout(5.seconds) {
            while (Files.isDirectory(sessionsDir)) {
                val remaining = Files.list(sessionsDir).use {
                    it.count()
                }
                if (remaining == 0L) {
                    break
                }
                delay(20)
            }
        }
    }
}
