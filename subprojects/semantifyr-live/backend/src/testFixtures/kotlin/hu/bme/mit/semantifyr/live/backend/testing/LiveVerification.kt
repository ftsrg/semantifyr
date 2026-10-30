/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.testing

import com.google.gson.Gson
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseSpecification
import hu.bme.mit.semantifyr.live.backend.testing.StagedTestSupport.config
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val gson = Gson()

suspend fun verifyEndToEnd(
    tmp: Path,
    flavor: String,
    modelSource: String,
    pickCase: (List<VerificationCaseSpecification>) -> VerificationCaseSpecification,
    assertVerdict: (result: VerificationCaseResult) -> Unit,
    languageId: String = "oxsts",
    documentUri: String = "file:///workspace/snippet.oxsts",
    discoverCommand: String = "oxsts.case.discover",
    verifyCommand: String = "oxsts.case.verify",
    verifyTimeout: Duration = 1.minutes,
) {
    StagedTestSupport.assumeStaged()
    withRealServer(config(tmp)) { client, port ->
        client.webSocket("ws://localhost:$port/ws/lsp/$flavor") {
            send(Frame.Text(LspWire.initializeRequest()))
            awaitResponseFor(id = 1)
            send(Frame.Text(LspWire.initializedNotification()))
            send(Frame.Text(LspWire.didOpenNotification(uri = documentUri, languageId = languageId, text = modelSource)))

            val cases = discoverCases(id = 2, command = discoverCommand, documentUri = documentUri, flavor = flavor)
            val selectedCase = pickCase(cases)

            send(
                Frame.Text(
                    LspWire.executeCommandRequest(
                        id = 3,
                        command = verifyCommand,
                        arguments = listOf(
                            VerificationCaseRequest(documentUri, selectedCase.location().range, "smart-full"),
                        ),
                    ),
                ),
            )
            val verifyResponse = awaitResponseFor(id = 3, timeout = verifyTimeout)
            assertVerdict(verifyResponse.resultAs(VerificationCaseResult::class.java))
        }
    }
}

suspend fun DefaultClientWebSocketSession.discoverCases(
    id: Int,
    command: String,
    documentUri: String,
    flavor: String,
): List<VerificationCaseSpecification> {
    send(
        Frame.Text(
            LspWire.executeCommandRequest(id = id, command = command, arguments = listOf(documentUri)),
        ),
    )
    val discoverResponse = awaitResponseFor(id = id)
    val resultJson = discoverResponse["result"]?.toString()
        ?: error("discover returned no result for flavor=$flavor: $discoverResponse")
    val cases = gson.fromJson(resultJson, Array<VerificationCaseSpecification>::class.java).toList()
    assertThat(cases).describedAs("discover for flavor=$flavor").isNotEmpty
    return cases
}

fun List<VerificationCaseSpecification>.pickByLabel(label: String): VerificationCaseSpecification {
    return firstOrNull {
        it.label() == label
    } ?: error("Expected '$label' verification case in the discovered list: $this")
}

fun List<VerificationCaseSpecification>.pickByLabelSuffix(suffix: String): VerificationCaseSpecification {
    return firstOrNull {
        it.label().endsWith(suffix)
    } ?: error("Expected verification case ending in '$suffix' in the discovered list: $this")
}

fun acceptAnyKnownStatus(label: String, result: VerificationCaseResult) {
    assertThat(result.status())
        .describedAs("verification status for $label: $result")
        .isIn("passed", "failed", "inconclusive", "not_supported")
}
