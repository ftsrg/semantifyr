/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.integration

import com.google.gson.Gson
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseSpecification
import hu.bme.mit.semantifyr.lang.ide.server.wire.WitnessValidationResult
import hu.bme.mit.semantifyr.lang.ide.server.wire.WitnessValidationStatus
import hu.bme.mit.semantifyr.live.backend.integration.IntegrationTestSupport.config
import hu.bme.mit.semantifyr.live.backend.integration.IntegrationTestSupport.stagedModel
import hu.bme.mit.semantifyr.live.backend.testing.LspWire
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class VerificationSmokeTest {

    private val gson = Gson()

    @Test
    suspend fun `oxsts verification case passes end-to-end`(@TempDir tmp: Path) {
        verifyEndToEnd(
            tmp = tmp,
            flavor = "oxsts",
            modelSource = stagedModel(IntegrationTestSupport.oxstsTestModelsDirectory, "semantifyr.live.oxstsTestModels", "trafficlight.oxsts"),
            pickCase = {
                it.pickByLabelSuffix("GreenColorIsReachable")
            },
            assertVerdict = {
                assertThat(it.status()).isEqualTo("passed")
            },
        )
    }

    @Test
    suspend fun `gamma verification case passes end-to-end`(@TempDir tmp: Path) {
        verifyEndToEnd(
            tmp = tmp,
            flavor = "gamma",
            languageId = "gamma",
            documentUri = "file:///workspace/snippet.gamma",
            modelSource = stagedModel(IntegrationTestSupport.gammaTestModelsDirectory, "semantifyr.live.gammaTestModels", "Simple.gamma"),
            discoverCommand = "gamma.case.discover",
            verifyCommand = "gamma.case.verify",
            pickCase = {
                val idleReachable = it.pickByLabel("LeaderStatechartIdleReachable")
                assertThat(idleReachable.id()).isEqualTo("Simple.LeaderStatechartIdleReachable")
                idleReachable
            },
            assertVerdict = {
                acceptAnyKnownStatus("LeaderStatechartIdleReachable", it)
                assertThat(it.status()).isIn("passed", "not_supported")
            },
            verifyTimeout = 2.minutes,
        )
    }

    @Test
    suspend fun `oxsts witness validation reports valid after a passing verification`(@TempDir tmp: Path) {
        IntegrationTestSupport.assumeStaged()
        val documentUri = "file:///workspace/snippet.oxsts"
        val modelSource = stagedModel(IntegrationTestSupport.oxstsTestModelsDirectory, "semantifyr.live.oxstsTestModels", "trafficlight.oxsts")
        withRealServer(config(tmp)) { client, port ->
            client.webSocket("ws://localhost:$port/ws/lsp/oxsts") {
                send(Frame.Text(LspWire.initializeRequest()))
                awaitResponseFor(id = 1)
                send(Frame.Text(LspWire.initializedNotification()))
                send(Frame.Text(LspWire.didOpenNotification(uri = documentUri, languageId = "oxsts", text = modelSource)))

                val cases = discoverCases(id = 2, command = "oxsts.case.discover", documentUri = documentUri, flavor = "oxsts")
                val selectedCase = cases.pickByLabelSuffix("GreenColorIsReachable")

                send(
                    Frame.Text(
                        LspWire.executeCommandRequest(
                            id = 3,
                            command = "oxsts.case.verify",
                            arguments = listOf(
                                VerificationCaseRequest(documentUri, selectedCase.location().range, "smart-full"),
                            ),
                        ),
                    ),
                )
                val verifyResponse = awaitResponseFor(id = 3, timeout = 1.minutes)
                val verifyResult = verifyResponse.resultAs(VerificationCaseResult::class.java)
                assertThat(verifyResult.status()).isEqualTo("passed")
                val witnessUri = verifyResult.trace()?.witnessUri()
                    ?: error("verify produced no witness URI: $verifyResult")

                send(Frame.Text(LspWire.didOpenNotification(uri = witnessUri, languageId = "oxsts", text = "")))

                send(
                    Frame.Text(
                        LspWire.executeCommandRequest(
                            id = 4,
                            command = "oxsts.case.validateWitness",
                            arguments = listOf(
                                VerificationCaseRequest(witnessUri, LspWire.range(), "smart-full"),
                            ),
                        ),
                    ),
                )
                val validateResponse = awaitResponseFor(id = 4, timeout = 1.minutes)
                val validation = validateResponse.resultAs(WitnessValidationResult::class.java)
                assertThat(validation.status())
                    .describedAs("witness validation: $validation")
                    .isEqualTo(WitnessValidationStatus.VALID)
            }
        }
    }

    @Test
    suspend fun `oxsts-with-gamma-library smoke verifies a compiled gamma example`(@TempDir tmp: Path) {
        smokeVerifyFirstCase(
            tmp = tmp,
            flavor = "oxsts-with-gamma-library",
            modelSource = stagedModel(IntegrationTestSupport.gammaLibraryModelsDirectory, "semantifyr.live.gammaLibraryModels", "Simple.oxsts"),
        )
    }

    @Test
    suspend fun `oxsts-with-sysmlv2-library smoke verifies a compiled sysml example`(@TempDir tmp: Path) {
        smokeVerifyFirstCase(
            tmp = tmp,
            flavor = "oxsts-with-sysmlv2-library",
            modelSource = stagedModel(IntegrationTestSupport.sysmlLibraryModelsDirectory, "semantifyr.live.sysmlLibraryModels", "door_access.oxsts"),
        )
    }

    private suspend fun smokeVerifyFirstCase(
        tmp: Path,
        flavor: String,
        modelSource: String,
    ) {
        verifyEndToEnd(
            tmp = tmp,
            flavor = flavor,
            modelSource = modelSource,
            pickCase = {
                it.first()
            },
            assertVerdict = {
                acceptAnyKnownStatus(flavor, it)
            },
            verifyTimeout = 2.minutes,
        )
    }

    private fun List<VerificationCaseSpecification>.pickByLabel(label: String): VerificationCaseSpecification {
        return firstOrNull {
            it.label() == label
        } ?: error("Expected '$label' verification case in the discovered list: $this")
    }

    private fun List<VerificationCaseSpecification>.pickByLabelSuffix(suffix: String): VerificationCaseSpecification {
        return firstOrNull {
            it.label().endsWith(suffix)
        } ?: error("Expected verification case ending in '$suffix' in the discovered list: $this")
    }

    private fun acceptAnyKnownStatus(label: String, result: VerificationCaseResult) {
        assertThat(result.status())
            .describedAs("verification status for $label: $result")
            .isIn("passed", "failed", "inconclusive", "not_supported")
    }

    private suspend fun verifyEndToEnd(
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
        IntegrationTestSupport.assumeStaged()
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

    private suspend fun DefaultClientWebSocketSession.discoverCases(
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
}
