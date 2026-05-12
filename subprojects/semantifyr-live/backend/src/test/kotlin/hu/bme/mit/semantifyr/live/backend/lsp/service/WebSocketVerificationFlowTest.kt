/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.service

import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.ServerConfig
import hu.bme.mit.semantifyr.live.backend.SessionManagerConfig
import hu.bme.mit.semantifyr.live.backend.lsp.session.LspSession
import hu.bme.mit.semantifyr.live.backend.lsp.session.VerificationExecutor
import hu.bme.mit.semantifyr.live.backend.testing.LspWire
import hu.bme.mit.semantifyr.live.backend.testing.awaitResponseCollectingNotifications
import hu.bme.mit.semantifyr.live.backend.testing.awaitResponseFor
import hu.bme.mit.semantifyr.live.backend.testing.installSemantifyrApp
import hu.bme.mit.semantifyr.live.backend.testing.installSemantifyrLiveBackend
import hu.bme.mit.semantifyr.live.backend.testing.jsonClient
import hu.bme.mit.semantifyr.live.backend.testing.testInjector
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class WebSocketVerificationFlowTest {

    private val cannedResult = VerificationCaseResult("passed", "ok", "theta-full", "smart-full", null, null)

    private fun config(tmp: Path): BackendConfig {
        return BackendConfig(
            server = ServerConfig(adminPassword = "test", wsHandshakesPerPeriod = 10_000),
            sessionManager = SessionManagerConfig(rootWorkDirectory = tmp.toString(), maxSessionsGlobal = 8),
        )
    }

    private class FakeVerificationExecutor(private val behaviour: () -> Any?) : VerificationExecutor {
        val calls = mutableListOf<ExecuteCommandParams>()
        override suspend fun execute(lspSession: LspSession, params: ExecuteCommandParams): Any? {
            calls += params
            return behaviour()
        }
    }

    private fun zeroRange(): JsonElement {
        return buildJsonObject {
            put(
                "start",
                buildJsonObject {
                    put("line", 0)
                    put("character", 0)
                },
            )
            put(
                "end",
                buildJsonObject {
                    put("line", 0)
                    put("character", 0)
                },
            )
        }
    }

    private fun verifyCommand(
        id: Int,
        command: String = "oxsts.case.verify",
        argument: JsonObject = LspWire.verifyCommandArgument(
            uri = "file:///workspace/snippet.oxsts",
            range = zeroRange(),
            portfolio = "smart-full",
            caseLabel = "CaseA",
        ),
    ): String {
        return LspWire.executeCommandRequest(id = id, command = command, arguments = listOf(argument))
    }

    @Test
    fun `verify command routes through the executor and returns its result`(@TempDir tmp: Path) = testApplication {
        val executor = FakeVerificationExecutor {
            cannedResult
        }

        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(tmp)) {
                bind(VerificationExecutor::class.java).toInstance(executor)
            })
        }

        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            awaitResponseFor(id = 1)
            send(Frame.Text(verifyCommand(id = 2)))
            val result = awaitResponseFor(id = 2)["result"]?.jsonObject ?: error("verify returned no result")
            assertThat(result["status"]?.jsonPrimitive?.contentOrNull).isEqualTo("passed")
            assertThat(result["portfolioId"]?.jsonPrimitive?.contentOrNull).isEqualTo("smart-full")
        }

        assertThat(executor.calls).hasSize(1)
        assertThat(executor.calls[0].command).isEqualTo("oxsts.case.verify")
    }

    @Test
    fun `validateWitness command also routes through the executor`(@TempDir tmp: Path) = testApplication {
        val executor = FakeVerificationExecutor { cannedResult }
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(tmp)) { bind(VerificationExecutor::class.java).toInstance(executor) })
        }

        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            awaitResponseFor(id = 1)
            send(Frame.Text(verifyCommand(id = 2, command = "oxsts.case.validateWitness")))
            assertThat(awaitResponseFor(id = 2)["result"]?.jsonObject).isNotNull
        }

        assertThat(executor.calls).hasSize(1)
        assertThat(executor.calls[0].command).isEqualTo("oxsts.case.validateWitness")
    }

    @Test
    fun `a verification that throws comes back as an errored result over the socket`(
        @TempDir tmp: Path,
    ) = testApplication {
        val executor = FakeVerificationExecutor {
            throw RuntimeException("verifier exploded")
        }
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(tmp)) { bind(VerificationExecutor::class.java).toInstance(executor) })
        }

        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            awaitResponseFor(id = 1)
            send(Frame.Text(verifyCommand(id = 2)))
            val result = awaitResponseFor(id = 2)["result"]?.jsonObject ?: error("verify returned no result")
            assertThat(result["status"]?.jsonPrimitive?.contentOrNull).isEqualTo("errored")
            assertThat(result["message"]?.jsonPrimitive?.contentOrNull).contains("verifier exploded")
            assertThat(result["portfolioId"]?.jsonPrimitive?.contentOrNull).isEqualTo("smart-full")
        }
    }

    @Test
    fun `verify command missing caseLabel is rejected with an InvalidParams error and skips the executor`(
        @TempDir tmp: Path,
    ) = testApplication {
        val executor = FakeVerificationExecutor {
            cannedResult
        }
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(tmp)) {
                bind(VerificationExecutor::class.java).toInstance(executor)
            })
        }

        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            awaitResponseFor(id = 1)
            val arg = buildJsonObject {
                put("uri", JsonPrimitive("file:///workspace/snippet.oxsts"))
                put("range", zeroRange())
                put("portfolio", JsonPrimitive("smart-full"))
            }
            send(Frame.Text(LspWire.executeCommandRequest(id = 2, command = "oxsts.case.verify", arguments = listOf(arg))))
            val error = awaitResponseFor(id = 2)["error"]?.jsonObject ?: error("expected a json-rpc error")
            assertThat(error["code"]?.jsonPrimitive?.int).isEqualTo(ResponseErrorCode.InvalidParams.value)
            assertThat(error["message"]?.jsonPrimitive?.contentOrNull).contains("caseLabel")
        }

        assertThat(executor.calls).isEmpty()
    }

    @Test
    fun `verify brackets the run with verificationsChanged notifications`(@TempDir tmp: Path) = testApplication {
        val executor = FakeVerificationExecutor {
            cannedResult
        }
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(tmp)) {
                bind(VerificationExecutor::class.java).toInstance(executor)
            })
        }

        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            awaitResponseFor(id = 1)
            val arg = LspWire.verifyCommandArgument(
                uri = "file:///workspace/snippet.oxsts",
                range = zeroRange(),
                portfolio = "smart-full",
                caseLabel = "CaseA",
                requestId = "req-1",
            )
            send(Frame.Text(LspWire.executeCommandRequest(id = 2, command = "oxsts.case.verify", arguments = listOf(arg))))
            val (response, notifications) = awaitResponseCollectingNotifications(
                id = 2,
                method = SemantifyrLiveMethods.VERIFICATIONS_CHANGED,
            )
            assertThat(response["result"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull).isEqualTo("passed")
            assertThat(notifications).hasSizeGreaterThanOrEqualTo(2)
            val firstActive = notifications.first()["params"]?.jsonObject?.get("active")?.jsonArray.orEmpty()
            val lastActive = notifications.last()["params"]?.jsonObject?.get("active")?.jsonArray.orEmpty()
            assertThat(firstActive.map {
                it.jsonObject["requestId"]?.jsonPrimitive?.contentOrNull
            }).containsExactly("req-1")
            assertThat(lastActive).isEmpty()
        }
    }

    @Test
    fun `non-throttled command bypasses the executor`(@TempDir tmp: Path) = testApplication {
        val executor = FakeVerificationExecutor {
            cannedResult
        }
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(testInjector(config(tmp)) {
                bind(VerificationExecutor::class.java).toInstance(executor)
            })
        }

        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            awaitResponseFor(id = 1)
            send(Frame.Text(LspWire.initializedNotification()))
            send(
                Frame.Text(
                    LspWire.didOpenNotification(
                        uri = "file:///workspace/snippet.oxsts",
                        languageId = "oxsts",
                        text = "package test\n\n@VerificationCase\nclass CaseA {\n    prop { return true }\n}\n",
                    ),
                ),
            )
            send(
                Frame.Text(
                    LspWire.executeCommandRequest(
                        id = 2,
                        command = "oxsts.case.discover",
                        arguments = listOf(JsonPrimitive("file:///workspace/snippet.oxsts")),
                    ),
                ),
            )
            assertThat(awaitResponseFor(id = 2)["result"] as? JsonArray).isNotEmpty
        }

        assertThat(executor.calls).isEmpty()
    }
}
