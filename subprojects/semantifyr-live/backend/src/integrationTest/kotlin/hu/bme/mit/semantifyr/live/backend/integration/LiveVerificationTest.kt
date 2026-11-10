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
import hu.bme.mit.semantifyr.live.backend.lsp.service.ReadDocumentParams
import hu.bme.mit.semantifyr.live.backend.lsp.service.ReadDocumentResult
import hu.bme.mit.semantifyr.live.backend.lsp.service.SemantifyrLiveMethods
import hu.bme.mit.semantifyr.live.backend.testing.LspWire
import hu.bme.mit.semantifyr.live.backend.testing.StagedTestSupport
import hu.bme.mit.semantifyr.live.backend.testing.StagedTestSupport.config
import hu.bme.mit.semantifyr.live.backend.testing.StagedTestSupport.stagedModel
import hu.bme.mit.semantifyr.live.backend.testing.acceptAnyKnownStatus
import hu.bme.mit.semantifyr.live.backend.testing.awaitPublishDiagnostics
import hu.bme.mit.semantifyr.live.backend.testing.awaitResponseFor
import hu.bme.mit.semantifyr.live.backend.testing.discoverCases
import hu.bme.mit.semantifyr.live.backend.testing.paramsAs
import hu.bme.mit.semantifyr.live.backend.testing.pickByLabel
import hu.bme.mit.semantifyr.live.backend.testing.pickByLabelSuffix
import hu.bme.mit.semantifyr.live.backend.testing.resultAs
import hu.bme.mit.semantifyr.live.backend.testing.verifyEndToEnd
import hu.bme.mit.semantifyr.live.backend.testing.withRealServer
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
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
                send(Frame.Text(LspWire.request(id = 5, method = SemantifyrLiveMethods.READ_DOCUMENT, params = ReadDocumentParams(witnessUri))))
                val witnessText = awaitResponseFor(id = 5).resultAs(ReadDocumentResult::class.java).text

                send(
                    Frame.Text(
                        LspWire.executeCommandRequest(
                            id = 6,
                            command = "oxsts.case.validateWitness",
                            arguments = listOf(
                                VerificationCaseRequest(witnessUri, LspWire.range(), "smart-full"),
                            ),
                        ),
                    ),
                )
                val revalidation = awaitResponseFor(id = 6, timeout = 1.minutes).resultAs(WitnessValidationResult::class.java)
                assertThat(revalidation.status())
                    .describedAs("second witness validation: $revalidation")
                    .isEqualTo(WitnessValidationStatus.VALID)
                send(Frame.Text(LspWire.request(id = 7, method = SemantifyrLiveMethods.READ_DOCUMENT, params = ReadDocumentParams(witnessUri))))
                assertThat(awaitResponseFor(id = 7).resultAs(ReadDocumentResult::class.java).text).isEqualTo(witnessText)
                assertThat(artifactsOnDisk(tmp, "snippet.CounterReachesThree")).contains(
                    "report.json",
                    "witness.oxsts",
                    "validation/report.json",
                )
            }
        }
    }

    @Test
    suspend fun `gamma verification returns the generated gamma trace, which opens without errors`(@TempDir tmp: Path) {
        StagedTestSupport.assumeStaged()
        val documentUri = "file:///workspace/snippet.gamma"
        val modelSource = stagedModel(StagedTestSupport.gammaTestModelsDirectory, "semantifyr.live.gammaTestModels", "Simple.gamma")
        withRealServer(config(tmp)) { client, port ->
            client.webSocket("ws://localhost:$port/ws/lsp/gamma") {
                send(Frame.Text(LspWire.initializeRequest()))
                awaitResponseFor(id = 1)
                send(Frame.Text(LspWire.initializedNotification()))
                send(Frame.Text(LspWire.didOpenNotification(uri = documentUri, languageId = "gamma", text = modelSource)))

                val cases = discoverCases(id = 2, command = "gamma.case.discover", documentUri = documentUri, flavor = "gamma")
                val selectedCase = cases.pickByLabel("LeaderStatechartIdleReachable")

                send(
                    Frame.Text(
                        LspWire.executeCommandRequest(
                            id = 3,
                            command = "gamma.case.verify",
                            arguments = listOf(
                                VerificationCaseRequest(documentUri, selectedCase.location().range, "smart-full"),
                            ),
                        ),
                    ),
                )
                val verifyResult = awaitResponseFor(id = 3, timeout = 2.minutes).resultAs(VerificationCaseResult::class.java)
                assertThat(verifyResult.status()).isEqualTo("passed")
                val traceUri = verifyResult.trace()?.witnessUri()
                    ?: error("verify produced no trace URI: $verifyResult")
                assertThat(traceUri).startsWith("file:///workspace/").endsWith("/trace.gamma")

                send(Frame.Text(LspWire.request(id = 4, method = SemantifyrLiveMethods.READ_DOCUMENT, params = ReadDocumentParams(traceUri))))
                val traceText = awaitResponseFor(id = 4).resultAs(ReadDocumentResult::class.java).text
                assertThat(traceText).contains("trace LeaderStatechartIdleReachableTrace of LeaderStatechartIdleReachable")

                send(Frame.Text(LspWire.didOpenNotification(uri = traceUri, languageId = "gamma", text = checkNotNull(traceText))))
                val diagnostics = awaitPublishDiagnostics(traceUri).paramsAs(PublishDiagnosticsParams::class.java).diagnostics
                assertThat(diagnostics).noneMatch {
                    it.severity == DiagnosticSeverity.Error
                }
            }
        }
    }

    @Test
    suspend fun `gamma witness validation re-verifies the witness of the verified case`(@TempDir tmp: Path) {
        StagedTestSupport.assumeStaged()
        val documentUri = "file:///workspace/snippet.gamma"
        val modelSource = stagedModel(StagedTestSupport.gammaTestModelsDirectory, "semantifyr.live.gammaTestModels", "Simple.gamma")
        withRealServer(config(tmp)) { client, port ->
            client.webSocket("ws://localhost:$port/ws/lsp/gamma") {
                send(Frame.Text(LspWire.initializeRequest()))
                awaitResponseFor(id = 1)
                send(Frame.Text(LspWire.initializedNotification()))
                send(Frame.Text(LspWire.didOpenNotification(uri = documentUri, languageId = "gamma", text = modelSource)))

                val cases = discoverCases(id = 2, command = "gamma.case.discover", documentUri = documentUri, flavor = "gamma")
                val request = VerificationCaseRequest(documentUri, cases.pickByLabel("LeaderStatechartIdleReachable").location().range, "smart-full")

                send(Frame.Text(LspWire.executeCommandRequest(id = 3, command = "gamma.witness.validate", arguments = listOf(request))))
                val missing = awaitResponseFor(id = 3).resultAs(WitnessValidationResult::class.java)
                assertThat(missing.status()).isEqualTo(WitnessValidationStatus.ERRORED)
                assertThat(missing.message()).endsWith("has no witness yet, verify it first")

                send(Frame.Text(LspWire.executeCommandRequest(id = 4, command = "gamma.case.verify", arguments = listOf(request))))
                val verifyResult = awaitResponseFor(id = 4, timeout = 2.minutes).resultAs(VerificationCaseResult::class.java)
                assertThat(verifyResult.status()).isEqualTo("passed")

                send(Frame.Text(LspWire.executeCommandRequest(id = 5, command = "gamma.witness.validate", arguments = listOf(request))))
                val validation = awaitResponseFor(id = 5, timeout = 2.minutes).resultAs(WitnessValidationResult::class.java)
                assertThat(validation.status())
                    .describedAs("witness validation: $validation")
                    .isEqualTo(WitnessValidationStatus.VALID)
                assertThat(validation.verdict()).isEqualTo("passed")
                assertThat(validation.portfolioId()).isEqualTo("smart-full")
                assertThat(artifactsOnDisk(tmp, "Simple.LeaderStatechartIdleReachable")).contains(
                    "verification/model.LeaderStatechartIdleReachable/report.json",
                    "verification/model.LeaderStatechartIdleReachable/witness.oxsts",
                    "verification/model.LeaderStatechartIdleReachable/trace.gamma",
                    "verification/model.LeaderStatechartIdleReachable/validation/report.json",
                )
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

    private fun artifactsOnDisk(root: Path, caseDirectoryName: String): List<String> {
        val caseDirectory = Files.walk(root).use {
            it.filter {
                Files.isDirectory(it) && it.fileName.toString() == caseDirectoryName
            }.toList()
        }.single()
        return Files.walk(caseDirectory).use {
            it.filter {
                Files.isRegularFile(it)
            }.map {
                caseDirectory.relativize(it).joinToString("/")
            }.toList()
        }
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
