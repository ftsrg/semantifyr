/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.integration

import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult
import hu.bme.mit.semantifyr.lang.ide.server.wire.WitnessValidationResult
import hu.bme.mit.semantifyr.lang.ide.server.wire.WitnessValidationStatus
import hu.bme.mit.semantifyr.live.backend.testing.LspWire
import hu.bme.mit.semantifyr.live.backend.testing.StagedTestSupport
import hu.bme.mit.semantifyr.live.backend.testing.StagedTestSupport.config
import hu.bme.mit.semantifyr.live.backend.testing.StagedTestSupport.stagedModel
import hu.bme.mit.semantifyr.live.backend.testing.acceptAnyKnownStatus
import hu.bme.mit.semantifyr.live.backend.testing.awaitResponseFor
import hu.bme.mit.semantifyr.live.backend.testing.discoverCases
import hu.bme.mit.semantifyr.live.backend.testing.pickByLabelSuffix
import hu.bme.mit.semantifyr.live.backend.testing.resultAs
import hu.bme.mit.semantifyr.live.backend.testing.verifyEndToEnd
import hu.bme.mit.semantifyr.live.backend.testing.withRealServer
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

class LiveVerificationTest {

    @Test
    suspend fun `oxsts witness validation reports valid after a passing verification`(@TempDir tmp: Path) {
        StagedTestSupport.assumeStaged()
        val documentUri = "file:///workspace/snippet.oxsts"
        val modelSource = stagedModel(StagedTestSupport.oxstsTestModelsDirectory, "semantifyr.live.oxstsTestModels", "smoke.oxsts")
        withRealServer(config(tmp)) { client, port ->
            client.webSocket("ws://localhost:$port/ws/lsp/oxsts") {
                send(Frame.Text(LspWire.initializeRequest()))
                awaitResponseFor(id = 1)
                send(Frame.Text(LspWire.initializedNotification()))
                send(Frame.Text(LspWire.didOpenNotification(uri = documentUri, languageId = "oxsts", text = modelSource)))

                val cases = discoverCases(id = 2, command = "oxsts.case.discover", documentUri = documentUri, flavor = "oxsts")
                val selectedCase = cases.pickByLabelSuffix("CounterReachesThree")

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
    suspend fun `oxsts-with-gamma-library verifies a compiled gamma example`(@TempDir tmp: Path) {
        verifyFirstCase(
            tmp = tmp,
            flavor = "oxsts-with-gamma-library",
            modelSource = stagedModel(StagedTestSupport.gammaLibraryModelsDirectory, "semantifyr.live.gammaLibraryModels", "Simple.oxsts"),
        )
    }

    @Test
    suspend fun `oxsts-with-sysmlv2-library verifies a compiled sysml example`(@TempDir tmp: Path) {
        verifyFirstCase(
            tmp = tmp,
            flavor = "oxsts-with-sysmlv2-library",
            modelSource = stagedModel(StagedTestSupport.sysmlLibraryModelsDirectory, "semantifyr.live.sysmlLibraryModels", "door_access.oxsts"),
        )
    }

    private suspend fun verifyFirstCase(
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
}
