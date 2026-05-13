/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.integration

import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseSpecification
import hu.bme.mit.semantifyr.live.backend.testing.LspWire
import hu.bme.mit.semantifyr.live.backend.testing.awaitPublishDiagnostics
import hu.bme.mit.semantifyr.live.backend.testing.awaitResponseFor
import hu.bme.mit.semantifyr.live.backend.testing.resultAs
import hu.bme.mit.semantifyr.live.backend.testing.withRealServer
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

class ConcurrentSessionTest {

    private val logger by loggerFactory()

    private val sessionCount = System.getProperty("semantifyr.live.scaleSessionCount")?.toIntOrNull() ?: 256

    private val model = """
        package scale

        @VerificationCase
        class CaseA {
            prop { return true }
        }
    """.trimIndent()

    @Test
    suspend fun `concurrent sessions all initialize, open, and discover`(@TempDir tmp: Path) {
        IntegrationTestSupport.assumeStaged()

        val startNanos = System.nanoTime()
        withRealServer(IntegrationTestSupport.config(tmp, maxSessionsGlobal = sessionCount)) { httpClient, port ->
            val failures = coroutineScope {
                (0 until sessionCount).map { index ->
                    async { runSession(httpClient, port, index) }
                }.awaitAll().filterNotNull()
            }
            logger.info {
                "Scale run: sessions=$sessionCount succeeded=${sessionCount - failures.size} failed=${failures.size} wallMillis=${(System.nanoTime() - startNanos) / 1_000_000} backendRssMiB=${backendRssMiB()} threads=${Thread.getAllStackTraces().size}"
            }
            assertThat(failures).describedAs {
                "failed sessions:\n${failures.joinToString("\n") { "  #${it.first}: ${it.second}" }}"
            }.isEmpty()
        }
    }

    private suspend fun runSession(
        httpClient: HttpClient,
        port: Int,
        index: Int,
    ): Pair<Int, String>? {
        return try {
            withTimeout(90.seconds) {
                httpClient.webSocket("ws://localhost:$port/ws/lsp/oxsts") {
                    val uri = "file:///workspace/s$index.oxsts"
                    send(Frame.Text(LspWire.initializeRequest()))
                    awaitResponseFor(id = 1)
                    send(Frame.Text(LspWire.initializedNotification()))
                    send(Frame.Text(LspWire.didOpenNotification(uri = uri, languageId = "oxsts", text = model)))
                    awaitPublishDiagnostics(uri)
                    send(
                        Frame.Text(
                            LspWire.executeCommandRequest(id = 2, command = "oxsts.case.discover", arguments = listOf(uri)),
                        ),
                    )
                    val discover = awaitResponseFor(id = 2)
                    val cases = discover.resultAs(Array<VerificationCaseSpecification>::class.java)
                    check(cases.isNotEmpty()) { "discover returned no cases: $discover" }
                }
            }
            null
        } catch (e: Exception) {
            index to "${e::class.simpleName}: ${e.message}"
        }
    }

    private fun backendRssMiB(): Long? {
        val status = Path.of("/proc/self/status")
        if (!Files.exists(status)) {
            return null
        }
        return runCatching {
            Files.readAllLines(status)
                .firstOrNull { it.startsWith("VmRSS:") }
                ?.split(Regex("\\s+"))
                ?.getOrNull(1)
                ?.toLong()
                ?.div(1024)
        }.getOrNull()
    }
}
