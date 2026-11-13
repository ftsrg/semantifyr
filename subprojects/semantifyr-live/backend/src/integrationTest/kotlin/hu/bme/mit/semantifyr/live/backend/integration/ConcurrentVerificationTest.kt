/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.integration

import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult
import hu.bme.mit.semantifyr.live.backend.VerificationConfig
import hu.bme.mit.semantifyr.live.backend.data.VerificationState
import hu.bme.mit.semantifyr.live.backend.lsp.service.SemantifyrLiveMethods
import hu.bme.mit.semantifyr.live.backend.lsp.service.VerificationsChangedParams
import hu.bme.mit.semantifyr.live.backend.testing.LspWire
import hu.bme.mit.semantifyr.live.backend.testing.StagedTestSupport
import hu.bme.mit.semantifyr.live.backend.testing.awaitPublishDiagnostics
import hu.bme.mit.semantifyr.live.backend.testing.awaitResponseFor
import hu.bme.mit.semantifyr.live.backend.testing.awaitResponsesCollectingNotifications
import hu.bme.mit.semantifyr.live.backend.testing.discoverCases
import hu.bme.mit.semantifyr.live.backend.testing.paramsAs
import hu.bme.mit.semantifyr.live.backend.testing.pickByLabelSuffix
import hu.bme.mit.semantifyr.live.backend.testing.resultAs
import hu.bme.mit.semantifyr.live.backend.testing.withRealServer
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

class ConcurrentVerificationTest {

    private val model = """
        package concurrent

        @VerificationCase
        class ReachesThree {
            var x: int := 0

            redefine tran {
                if (x < 3) {
                    x := x + 1
                }
            }

            prop {
                return EF x == 3
            }
        }

        @VerificationCase
        class ReachesTwo {
            var x: int := 0

            redefine tran {
                if (x < 2) {
                    x := x + 1
                }
            }

            prop {
                return EF x == 2
            }
        }

        @VerificationCase
        class StaysBelowThree {
            var x: int := 0

            redefine tran {
                if (x < 3) {
                    x := x + 1
                }
            }

            prop {
                return AG x < 3
            }
        }
    """.trimIndent()

    private val expectedVerdicts = mapOf(
        "ReachesThree" to "passed",
        "ReachesTwo" to "passed",
        "StaysBelowThree" to "failed",
    )

    @Test
    suspend fun `one session verifies more cases at once than there are permits and every case gets its verdict`(@TempDir tmp: Path) {
        StagedTestSupport.assumeStaged()
        val documentUri = "file:///workspace/concurrent.oxsts"
        withRealServer(StagedTestSupport.config(tmp, verification = VerificationConfig(concurrency = 2))) { client, port ->
            client.webSocket("ws://localhost:$port/ws/lsp/oxsts") {
                send(Frame.Text(LspWire.initializeRequest()))
                awaitResponseFor(id = 1)
                send(Frame.Text(LspWire.initializedNotification()))
                send(Frame.Text(LspWire.didOpenNotification(uri = documentUri, languageId = "oxsts", text = model)))
                awaitPublishDiagnostics(documentUri)

                val cases = discoverCases(id = 2, command = "oxsts.case.discover", documentUri = documentUri, flavor = "oxsts")
                val requestIds = expectedVerdicts.keys.withIndex().associate {
                    it.value to 10 + it.index
                }
                for ((name, requestId) in requestIds) {
                    val case = cases.pickByLabelSuffix(name)
                    send(
                        Frame.Text(
                            LspWire.executeCommandRequest(
                                id = requestId,
                                command = "oxsts.case.verify",
                                arguments = listOf(VerificationCaseRequest(documentUri, case.location().range, "smart")),
                            ),
                        ),
                    )
                }

                val (responses, notifications) = awaitResponsesCollectingNotifications(
                    ids = requestIds.values.toSet(),
                    method = SemantifyrLiveMethods.VERIFICATIONS_CHANGED,
                    timeout = 3.minutes,
                )

                for ((name, requestId) in requestIds) {
                    val result = checkNotNull(responses[requestId]).resultAs(VerificationCaseResult::class.java)
                    assertThat(result.status())
                        .describedAs("verdict of $name: $result")
                        .isEqualTo(expectedVerdicts.getValue(name))
                }
                val statesByVerification = notifications.flatMap {
                    it.paramsAs(VerificationsChangedParams::class.java).active
                }.groupBy(
                    keySelector = {
                        it.verificationId
                    },
                    valueTransform = {
                        it.state
                    },
                )
                val waited = statesByVerification.values.filter {
                    it.first() == VerificationState.Queued
                }
                assertThat(waited).describedAs("a case that had to wait: $statesByVerification").isNotEmpty()
                assertThat(waited).allSatisfy {
                    assertThat(it).contains(VerificationState.Running)
                }
            }
        }
    }
}
