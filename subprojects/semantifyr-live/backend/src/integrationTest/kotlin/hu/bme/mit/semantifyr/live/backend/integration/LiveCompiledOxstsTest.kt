/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.integration

import hu.bme.mit.semantifyr.lang.ide.server.wire.CompiledOxstsResult
import hu.bme.mit.semantifyr.live.backend.testing.LspWire
import hu.bme.mit.semantifyr.live.backend.testing.StagedTestSupport
import hu.bme.mit.semantifyr.live.backend.testing.StagedTestSupport.config
import hu.bme.mit.semantifyr.live.backend.testing.StagedTestSupport.stagedModel
import hu.bme.mit.semantifyr.live.backend.testing.awaitResponseFor
import hu.bme.mit.semantifyr.live.backend.testing.resultAs
import hu.bme.mit.semantifyr.live.backend.testing.withRealServer
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class LiveCompiledOxstsTest {

    private val documentUri = "file:///workspace/snippet.gamma"

    private suspend fun DefaultClientWebSocketSession.openGammaModel(text: String) {
        send(Frame.Text(LspWire.initializeRequest()))
        awaitResponseFor(id = 1)
        send(Frame.Text(LspWire.initializedNotification()))
        send(Frame.Text(LspWire.didOpenNotification(uri = documentUri, languageId = "gamma", text = text)))
    }

    private suspend fun DefaultClientWebSocketSession.compiledOxsts(id: Int): CompiledOxstsResult {
        val request = LspWire.executeCommandRequest(id = id, command = "gamma.compiled.oxsts", arguments = listOf(documentUri))
        send(Frame.Text(request))
        return awaitResponseFor(id = id).resultAs(CompiledOxstsResult::class.java)
    }

    @Test
    suspend fun `the gamma flavor returns the compiled oxsts of its document and follows its edits`(@TempDir temporaryDirectory: Path) {
        StagedTestSupport.assumeStaged()
        val modelSource = stagedModel(StagedTestSupport.gammaTestModelsDirectory, "semantifyr.live.gammaTestModels", "Simple.gamma")
        withRealServer(config(temporaryDirectory)) { client, port ->
            client.webSocket("ws://localhost:$port/ws/lsp/gamma") {
                openGammaModel(modelSource)

                val compiled = compiledOxsts(id = 2)
                assertThat(compiled.status()).isEqualTo(CompiledOxstsResult.COMPILED)
                assertThat(compiled.oxsts()).startsWith("package Simple")

                send(Frame.Text(LspWire.didChangeNotification(documentUri, 2, modelSource.replace("package Simple", "package Renamed"))))
                assertThat(compiledOxsts(id = 3).oxsts()).startsWith("package Renamed")
            }
        }
    }

    @Test
    suspend fun `the gamma flavor answers a model that does not compile with an errored result and keeps the session open`(@TempDir temporaryDirectory: Path) {
        StagedTestSupport.assumeStaged()
        withRealServer(config(temporaryDirectory)) { client, port ->
            client.webSocket("ws://localhost:$port/ws/lsp/gamma") {
                openGammaModel("package Broken\n\n} component ;; {\n")

                val compiled = compiledOxsts(id = 2)
                assertThat(compiled.status()).isEqualTo(CompiledOxstsResult.ERRORED)
                assertThat(compiled.oxsts()).isNull()
                assertThat(compiled.message()).startsWith("The model has a syntax error")
                assertThat(compiledOxsts(id = 3).status()).isEqualTo(CompiledOxstsResult.ERRORED)
            }
        }
    }

    @Test
    suspend fun `the oxsts flavor does not offer the command`(@TempDir temporaryDirectory: Path) {
        StagedTestSupport.assumeStaged()
        withRealServer(config(temporaryDirectory)) { client, port ->
            client.webSocket("ws://localhost:$port/ws/lsp/oxsts") {
                send(Frame.Text(LspWire.initializeRequest()))
                awaitResponseFor(id = 1)
                send(Frame.Text(LspWire.executeCommandRequest(id = 2, command = "gamma.compiled.oxsts", arguments = listOf(documentUri))))
                val response = awaitResponseFor(id = 2)
                assertThat(response.has("error")).isTrue
                assertThat(response.has("result")).isFalse
            }
        }
    }
}
