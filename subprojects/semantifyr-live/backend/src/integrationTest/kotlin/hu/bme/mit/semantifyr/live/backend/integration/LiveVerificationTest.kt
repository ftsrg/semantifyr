/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.integration

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseSpecification
import hu.bme.mit.semantifyr.lang.ide.server.wire.WitnessValidationRequest
import hu.bme.mit.semantifyr.lang.ide.server.wire.WitnessValidationResult
import hu.bme.mit.semantifyr.lang.ide.server.wire.WitnessValidationStatus
import hu.bme.mit.semantifyr.live.backend.lsp.service.ReadDocumentParams
import hu.bme.mit.semantifyr.live.backend.lsp.service.ReadDocumentResult
import hu.bme.mit.semantifyr.live.backend.lsp.service.SemantifyrLiveMethods
import hu.bme.mit.semantifyr.live.backend.testing.LspWire
import hu.bme.mit.semantifyr.live.backend.testing.StagedTestSupport
import hu.bme.mit.semantifyr.live.backend.testing.StagedTestSupport.config
import hu.bme.mit.semantifyr.live.backend.testing.StagedTestSupport.stagedModel
import hu.bme.mit.semantifyr.live.backend.testing.awaitPublishDiagnostics
import hu.bme.mit.semantifyr.live.backend.testing.awaitResponseFor
import hu.bme.mit.semantifyr.live.backend.testing.discoverCases
import hu.bme.mit.semantifyr.live.backend.testing.paramsAs
import hu.bme.mit.semantifyr.live.backend.testing.pickByLabel
import hu.bme.mit.semantifyr.live.backend.testing.pickByLabelSuffix
import hu.bme.mit.semantifyr.live.backend.testing.resultAs
import hu.bme.mit.semantifyr.live.backend.testing.verifyEndToEnd
import hu.bme.mit.semantifyr.live.backend.testing.withRealServer
import hu.bme.mit.semantifyr.verifier.CaseManifest
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
    suspend fun `an oxsts witness validates twice as valid, leaves the witness as it was and keeps its report beside the case's artifacts`(@TempDir temporaryDirectory: Path) {
        StagedTestSupport.assumeStaged()
        val documentUri = "file:///workspace/snippet.semantifyr"
        val modelSource = stagedModel(StagedTestSupport.oxstsTestModelsDirectory, "semantifyr.live.oxstsTestModels", "smoke.semantifyr")
        withRealServer(config(temporaryDirectory)) { client, port ->
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
                                VerificationCaseRequest(documentUri, selectedCase.location().range, "smart"),
                            ),
                        ),
                    ),
                )
                val verifyResponse = awaitResponseFor(id = 3, timeout = 1.minutes)
                val verifyResult = verifyResponse.resultAs(VerificationCaseResult::class.java)
                assertThat(verifyResult.outcomeStatus()).isEqualTo("passed")
                val witnessUri = verifyResult.witnessUri()
                    ?: error("verify produced no witness URI: $verifyResult")

                send(Frame.Text(LspWire.didOpenNotification(uri = witnessUri, languageId = "oxsts", text = "")))

                send(
                    Frame.Text(
                        LspWire.executeCommandRequest(
                            id = 4,
                            command = "oxsts.case.validateWitness",
                            arguments = listOf(
                                VerificationCaseRequest(witnessUri, LspWire.range(), "smart"),
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
                                VerificationCaseRequest(witnessUri, LspWire.range(), "smart"),
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
                assertThat(artifactsOnDisk(temporaryDirectory, caseDirectoryName("snippet", selectedCase))).contains(
                    "report.json",
                    "witness.semantifyr",
                    "validation/report.json",
                )
            }
        }
    }

    @Test
    suspend fun `a gamma verification returns its gamma trace, which opens without errors, and a witness that re-verifies`(@TempDir temporaryDirectory: Path) {
        StagedTestSupport.assumeStaged()
        val documentUri = "file:///workspace/snippet.gamma"
        val modelSource = stagedModel(StagedTestSupport.gammaExamplesDirectory, "semantifyr.live.gammaExamples", "Simple.gamma")
        withRealServer(config(temporaryDirectory)) { client, port ->
            client.webSocket("ws://localhost:$port/ws/lsp/gamma") {
                send(Frame.Text(LspWire.initializeRequest()))
                awaitResponseFor(id = 1)
                send(Frame.Text(LspWire.initializedNotification()))
                send(Frame.Text(LspWire.didOpenNotification(uri = documentUri, languageId = "gamma", text = modelSource)))

                val cases = discoverCases(id = 2, command = "gamma.case.discover", documentUri = documentUri, flavor = "gamma")
                val selectedCase = cases.pickByLabel("LeaderStatechartIdleReachable")
                val request = VerificationCaseRequest(documentUri, selectedCase.location().range, "smart")

                send(Frame.Text(LspWire.executeCommandRequest(id = 3, command = "gamma.case.verify", arguments = listOf(request))))
                val verifyResult = awaitResponseFor(id = 3, timeout = 2.minutes).resultAs(VerificationCaseResult::class.java)
                assertThat(verifyResult.outcomeStatus()).isEqualTo(expectedStatus("Simple.gamma", "LeaderStatechartIdleReachable"))
                val traceUri = verifyResult.traceUri()
                    ?: error("verify produced no trace URI: $verifyResult")
                assertThat(traceUri).startsWith("file:///workspace/").endsWith("/trace.get")

                send(Frame.Text(LspWire.request(id = 4, method = SemantifyrLiveMethods.READ_DOCUMENT, params = ReadDocumentParams(traceUri))))
                val traceText = awaitResponseFor(id = 4).resultAs(ReadDocumentResult::class.java).text
                assertThat(traceText).contains("trace LeaderStatechartIdleReachableTrace of LeaderStatechartIdleReachable")
                send(Frame.Text(LspWire.didOpenNotification(uri = traceUri, languageId = "gamma", text = checkNotNull(traceText))))
                val diagnostics = awaitPublishDiagnostics(traceUri).paramsAs(PublishDiagnosticsParams::class.java).diagnostics
                assertThat(diagnostics).noneMatch {
                    it.severity == DiagnosticSeverity.Error
                }

                val witnessUri = verifyResult.witnessUri()
                    ?: error("verify produced no witness URI: $verifyResult")
                assertThat(witnessUri).startsWith("file:///workspace/")
                val witnessRequest = WitnessValidationRequest(documentUri, selectedCase.location().range, "smart", witnessUri)
                send(Frame.Text(LspWire.executeCommandRequest(id = 5, command = "gamma.witness.validate", arguments = listOf(witnessRequest))))
                val validation = awaitResponseFor(id = 5, timeout = 2.minutes).resultAs(WitnessValidationResult::class.java)
                assertThat(validation.status())
                    .describedAs("witness validation: $validation")
                    .isEqualTo(WitnessValidationStatus.VALID)
                assertThat(validation.report()?.result?.verdict).isEqualTo(VerificationVerdict.Passed)
                assertThat(validation.portfolioId()).isEqualTo("smart")
                assertThat(artifactsOnDisk(temporaryDirectory, "model._Simple._LeaderStatechartIdleReachable")).contains(
                    "report.json",
                    "witness.semantifyr",
                    "trace.get",
                    "validation/report.json",
                )
            }
        }
    }

    @Test
    suspend fun `oxsts-with-gamma-library verifies a compiled gamma example`(@TempDir temporaryDirectory: Path) {
        verifyCompiledCase(
            temporaryDirectory = temporaryDirectory,
            flavor = "oxsts-with-gamma-library",
            modelSource = stagedModel(StagedTestSupport.gammaCompiledExamplesDirectory, "semantifyr.live.gammaCompiledExamples", "Simple.semantifyr"),
            caseName = "LeaderStatechartIdleReachable",
            expectedStatus = expectedStatus("Simple.gamma", "LeaderStatechartIdleReachable"),
        )
    }

    @Test
    suspend fun `oxsts-with-sysmlv2-library verifies a compiled sysml example`(@TempDir temporaryDirectory: Path) {
        verifyCompiledCase(
            temporaryDirectory = temporaryDirectory,
            flavor = "oxsts-with-sysmlv2-library",
            modelSource = stagedModel(StagedTestSupport.sysmlCompiledExamplesDirectory, "semantifyr.live.sysmlCompiledExamples", "door_access.semantifyr"),
            caseName = "LockedReachable",
            expectedStatus = "passed",
        )
    }

    // the wire's status of a case's expected verdict, which the manifest beside the staged Gamma models states
    private fun expectedStatus(file: String, caseName: String): String {
        return CaseManifest.load(checkNotNull(StagedTestSupport.gammaExamplesDirectory)).expectedVerdict(file, caseName).name.lowercase()
    }

    private fun caseDirectoryName(modelStem: String, case: VerificationCaseSpecification): String {
        return "$modelStem.${case.id().replace("::", ".")}"
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

    private suspend fun verifyCompiledCase(
        temporaryDirectory: Path,
        flavor: String,
        modelSource: String,
        caseName: String,
        expectedStatus: String,
    ) {
        verifyEndToEnd(
            temporaryDirectory = temporaryDirectory,
            flavor = flavor,
            modelSource = modelSource,
            pickCase = {
                it.pickByLabelSuffix(caseName)
            },
            assertVerdict = {
                assertThat(it.outcomeStatus()).describedAs("$flavor $caseName: $it").isEqualTo(expectedStatus)
            },
            verifyTimeout = 2.minutes,
        )
    }
}
