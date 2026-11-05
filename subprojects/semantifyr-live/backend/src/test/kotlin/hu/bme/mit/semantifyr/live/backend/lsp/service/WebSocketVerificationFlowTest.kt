/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.service

import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseSpecification
import hu.bme.mit.semantifyr.lang.ide.server.wire.WitnessValidationResult
import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.ServerConfig
import hu.bme.mit.semantifyr.live.backend.SessionManagerConfig
import hu.bme.mit.semantifyr.live.backend.VerificationConfig
import hu.bme.mit.semantifyr.live.backend.data.VerificationKind
import hu.bme.mit.semantifyr.live.backend.lsp.document.SessionDocumentManager
import hu.bme.mit.semantifyr.live.backend.lsp.session.VerificationExecutor
import hu.bme.mit.semantifyr.live.backend.testing.LspWire
import hu.bme.mit.semantifyr.live.backend.testing.awaitResponseCollectingNotifications
import hu.bme.mit.semantifyr.live.backend.testing.awaitResponseFor
import hu.bme.mit.semantifyr.live.backend.testing.errorAs
import hu.bme.mit.semantifyr.live.backend.testing.installSemantifyrApp
import hu.bme.mit.semantifyr.live.backend.testing.installSemantifyrLiveBackend
import hu.bme.mit.semantifyr.live.backend.testing.jsonClient
import hu.bme.mit.semantifyr.live.backend.testing.paramsAs
import hu.bme.mit.semantifyr.live.backend.testing.resultAs
import hu.bme.mit.semantifyr.live.backend.testing.testInjector
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

class WebSocketVerificationFlowTest {

    private val cannedResult = VerificationCaseResult("passed", "ok", "theta-full", "smart-full", null, null)

    private val documentUri = "file:///workspace/snippet.oxsts"

    private fun config(tmp: Path): BackendConfig {
        return BackendConfig(
            server = ServerConfig(wsHandshakesPerPeriod = 10_000),
            sessionManager = SessionManagerConfig(rootWorkDirectory = tmp.toString(), maxSessionsGlobal = 8),
        )
    }

    private class FakeVerificationExecutor(private val behaviour: suspend (ExecuteCommandParams) -> Any?) : VerificationExecutor {
        val calls = mutableListOf<ExecuteCommandParams>()
        override suspend fun execute(
            sessionRequestManager: SessionRequestManager,
            sessionDocumentManager: SessionDocumentManager,
            params: ExecuteCommandParams,
        ): Any? {
            calls += params
            return behaviour(params)
        }
    }

    private fun verifyRequest(uri: String = documentUri, portfolio: String? = "smart-full"): VerificationCaseRequest {
        return VerificationCaseRequest(uri, LspWire.range(), portfolio)
    }

    private fun verifyCommand(
        id: Int,
        command: String = "oxsts.case.verify",
        argument: Any = verifyRequest(),
    ): String {
        return LspWire.executeCommandRequest(id = id, command = command, arguments = listOf(argument))
    }

    @Test
    fun `verify command routes through the executor and returns its result`(@TempDir tmp: Path) = testApplication {
        val executor = FakeVerificationExecutor { cannedResult }
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(
                testInjector(config(tmp)) {
                    bind(VerificationExecutor::class.java).toInstance(executor)
                },
            )
        }

        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            awaitResponseFor(id = 1)
            send(Frame.Text(verifyCommand(id = 2)))
            val result = awaitResponseFor(id = 2).resultAs(VerificationCaseResult::class.java)
            assertThat(result.status()).isEqualTo("passed")
            assertThat(result.portfolioId()).isEqualTo("smart-full")
        }

        assertThat(executor.calls).hasSize(1)
        assertThat(executor.calls[0].command).isEqualTo("oxsts.case.verify")
    }

    @Test
    fun `validateWitness command also routes through the executor`(@TempDir tmp: Path) = testApplication {
        val executor = FakeVerificationExecutor { cannedResult }
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(
                testInjector(config(tmp)) {
                    bind(VerificationExecutor::class.java).toInstance(executor)
                },
            )
        }

        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            awaitResponseFor(id = 1)
            send(Frame.Text(verifyCommand(id = 2, command = "oxsts.case.validateWitness")))
            val result = awaitResponseFor(id = 2).resultAs(VerificationCaseResult::class.java)
            assertThat(result.status()).isEqualTo("passed")
        }

        assertThat(executor.calls).hasSize(1)
        assertThat(executor.calls[0].command).isEqualTo("oxsts.case.validateWitness")
    }

    @Test
    fun `a verification that throws comes back as an errored result over the socket`(@TempDir tmp: Path) = testApplication {
        val executor = FakeVerificationExecutor {
            throw RuntimeException("verifier exploded")
        }
        val injector = testInjector(config(tmp)) {
            bind(VerificationExecutor::class.java).toInstance(executor)
        }
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(injector)
        }

        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            awaitResponseFor(id = 1)
            send(Frame.Text(verifyCommand(id = 2)))
            val result = awaitResponseFor(id = 2).resultAs(VerificationCaseResult::class.java)
            assertThat(result.status()).isEqualTo("errored")
            assertThat(result.message()).contains("verifier exploded")
            assertThat(result.portfolioId()).isEqualTo("smart-full")
        }

        assertThat(verificationCount(injector.getInstance(MeterRegistry::class.java), "verify", "none", "errored")).isEqualTo(1)
    }

    @Test
    fun `a verification that outlasts the session timeout comes back as a timeout result over the socket`(@TempDir tmp: Path) = testApplication {
        val executor = FakeVerificationExecutor {
            awaitCancellation()
        }
        val injector = testInjector(config(tmp).copy(verification = VerificationConfig(timeout = 200.milliseconds))) {
            bind(VerificationExecutor::class.java).toInstance(executor)
        }
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(injector)
        }

        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            awaitResponseFor(id = 1)
            send(Frame.Text(verifyCommand(id = 2)))
            val result = awaitResponseFor(id = 2).resultAs(VerificationCaseResult::class.java)
            assertThat(result.status()).isEqualTo("timeout")
            assertThat(result.message()).contains("timed out")
            assertThat(result.portfolioId()).isEqualTo("smart-full")
        }

        assertThat(verificationCount(injector.getInstance(MeterRegistry::class.java), "verify", "none", "timeout")).isEqualTo(1)
    }

    @Test
    fun `verify command missing the portfolio is rejected with an InvalidParams error and skips the executor`(
        @TempDir tmp: Path,
    ) = testApplication {
        val executor = FakeVerificationExecutor { cannedResult }
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(
                testInjector(config(tmp)) {
                    bind(VerificationExecutor::class.java).toInstance(executor)
                },
            )
        }

        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            awaitResponseFor(id = 1)
            send(Frame.Text(verifyCommand(id = 2, argument = verifyRequest(portfolio = null))))
            val error = awaitResponseFor(id = 2).errorAs(ResponseError::class.java)
            assertThat(error.code).isEqualTo(ResponseErrorCode.InvalidParams.value)
            assertThat(error.message).contains("portfolio")
        }

        assertThat(executor.calls).isEmpty()
    }

    @Test
    fun `verify brackets the run with verificationsChanged notifications carrying the case`(@TempDir tmp: Path) = testApplication {
        val executor = FakeVerificationExecutor { cannedResult }
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(
                testInjector(config(tmp)) {
                    bind(VerificationExecutor::class.java).toInstance(executor)
                },
            )
        }

        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            awaitResponseFor(id = 1)
            send(Frame.Text(verifyCommand(id = 2)))
            val (response, notifications) = awaitResponseCollectingNotifications(
                id = 2,
                method = SemantifyrLiveMethods.VERIFICATIONS_CHANGED,
            )
            assertThat(response.resultAs(VerificationCaseResult::class.java).status()).isEqualTo("passed")
            assertThat(notifications).hasSizeGreaterThanOrEqualTo(2)
            val firstActive = notifications.first().paramsAs(VerificationsChangedParams::class.java).active
            val lastActive = notifications.last().paramsAs(VerificationsChangedParams::class.java).active
            assertThat(firstActive).hasSize(1)
            val entry = firstActive.first()
            assertThat(entry.verificationId).isNotBlank()
            assertThat(entry.portfolioId).isEqualTo("smart-full")
            assertThat(entry.kind).isEqualTo(VerificationKind.Verify)
            assertThat(entry.location.uri).isEqualTo(documentUri)
            assertThat(lastActive).isEmpty()
        }
    }

    @Test
    fun `non-throttled command bypasses the executor`(@TempDir tmp: Path) = testApplication {
        val executor = FakeVerificationExecutor { cannedResult }
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(
                testInjector(config(tmp)) {
                    bind(VerificationExecutor::class.java).toInstance(executor)
                },
            )
        }

        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            awaitResponseFor(id = 1)
            send(Frame.Text(LspWire.initializedNotification()))
            send(
                Frame.Text(
                    LspWire.didOpenNotification(
                        uri = documentUri,
                        languageId = "oxsts",
                        text = "package test\n\n@VerificationCase\nclass CaseA {\n    prop {\n        return true\n    }\n}\n",
                    ),
                ),
            )
            send(
                Frame.Text(
                    LspWire.executeCommandRequest(
                        id = 2,
                        command = "oxsts.case.discover",
                        arguments = listOf(documentUri),
                    ),
                ),
            )
            val cases = awaitResponseFor(id = 2).resultAs(Array<VerificationCaseSpecification>::class.java)
            assertThat(cases).isNotEmpty
        }

        assertThat(executor.calls).isEmpty()
    }

    @Test
    fun `finished verifications are timed by kind, backend and verdict`(@TempDir tmp: Path) = testApplication {
        val executor = FakeVerificationExecutor {
            if (it.command == "oxsts.case.validateWitness") {
                WitnessValidationResult("valid", null, null, "theta-full", "smart-full", null)
            } else {
                cannedResult
            }
        }
        val injector = testInjector(config(tmp)) {
            bind(VerificationExecutor::class.java).toInstance(executor)
        }
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(injector)
        }

        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            awaitResponseFor(id = 1)
            send(Frame.Text(verifyCommand(id = 2)))
            awaitResponseFor(id = 2)
            send(Frame.Text(verifyCommand(id = 3)))
            awaitResponseFor(id = 3)
            send(Frame.Text(verifyCommand(id = 4, command = "oxsts.case.validateWitness")))
            awaitResponseFor(id = 4)
        }

        val registry = injector.getInstance(MeterRegistry::class.java)
        assertThat(verificationCount(registry, "verify", "theta-full", "passed")).isEqualTo(2)
        assertThat(verificationCount(registry, "validate", "theta-full", "valid")).isEqualTo(1)
        assertThat(registry.get("semantifyr.live.verification.queue").tag("kind", "verify").timer().count()).isEqualTo(2)
    }

    @Test
    fun `a cancelled verification is counted with the state it was cancelled in`(@TempDir tmp: Path) = testApplication {
        val running = CompletableDeferred<Unit>()
        val executor = FakeVerificationExecutor {
            running.complete(Unit)
            awaitCancellation()
        }
        val injector = testInjector(config(tmp)) {
            bind(VerificationExecutor::class.java).toInstance(executor)
        }
        installSemantifyrApp(webSockets = true) {
            installSemantifyrLiveBackend(injector)
        }

        jsonClient(webSockets = true).webSocket("/ws/lsp/oxsts") {
            send(Frame.Text(LspWire.initializeRequest()))
            awaitResponseFor(id = 1)
            send(Frame.Text(verifyCommand(id = 2)))
            running.await()
            send(Frame.Text(LspWire.request(id = 3, method = SemantifyrLiveMethods.CANCEL_ALL_VERIFICATIONS)))
            awaitResponseFor(id = 2)
        }

        val registry = injector.getInstance(MeterRegistry::class.java)
        val cancellations = registry.get("semantifyr.live.verification.cancellations")
            .tag("kind", "verify")
            .tag("state", "running")
            .counter()
        assertThat(cancellations.count()).isEqualTo(1.0)
        assertThat(registry.find("semantifyr.live.verification").timer()).isNull()
    }

    private fun verificationCount(
        registry: MeterRegistry,
        kind: String,
        backend: String,
        verdict: String,
    ): Long {
        return registry.get("semantifyr.live.verification")
            .tag("kind", kind)
            .tag("backend", backend)
            .tag("verdict", verdict)
            .timer()
            .count()
    }
}
