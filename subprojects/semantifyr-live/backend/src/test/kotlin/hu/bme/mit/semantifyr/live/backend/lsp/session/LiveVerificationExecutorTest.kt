/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.session

import com.google.gson.JsonElement
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandGson
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult
import hu.bme.mit.semantifyr.lang.ide.server.wire.WitnessValidationRequest
import hu.bme.mit.semantifyr.live.backend.lsp.document.SessionDocument
import hu.bme.mit.semantifyr.live.backend.lsp.document.SessionDocumentManager
import hu.bme.mit.semantifyr.live.backend.lsp.service.SessionRequestManager
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.xtext.util.CancelIndicator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture

class LiveVerificationExecutorTest {

    private val executor = LiveVerificationExecutor()

    private class Fixture(rawResult: Any?) {
        val documents = mock<SessionDocumentManager>()
        val requestManager = mock<SessionRequestManager> {
            on { executeCommand(any()) } doReturn CompletableFuture.completedFuture(rawResult)
        }

        fun runWritesInline() {
            whenever(requestManager.runWrite<Any?>(any(), any())) doAnswer {
                @Suppress("UNCHECKED_CAST")
                val mutation = it.arguments[0] as () -> Any?

                @Suppress("UNCHECKED_CAST")
                val validation = it.arguments[1] as (CancelIndicator, Any?) -> Unit
                val result = mutation()
                validation(CancelIndicator.NullImpl, result)
                CompletableFuture.completedFuture(result)
            }
        }
    }

    private fun noArgs() = ExecuteCommandParams("oxsts.case.verify", emptyList())

    private fun argsWithUri(uri: String) = ExecuteCommandParams(
        "oxsts.case.verify",
        listOf<Any>(CommandGson.INSTANCE.toJsonTree(VerificationCaseRequest(uri, null, null))),
    )

    @Test
    fun `a non-verification-result is returned unchanged`() = runTest {
        val fixture = Fixture("plain-result")
        assertThat(executor.execute(fixture.requestManager, fixture.documents, noArgs())).isEqualTo("plain-result")
    }

    @Test
    fun `a result without a trace is returned unchanged`() = runTest {
        val raw = VerificationCaseResult("passed", "ok", "smart", null, null, null)
        val fixture = Fixture(raw)
        assertThat(executor.execute(fixture.requestManager, fixture.documents, noArgs())).isEqualTo(raw)
    }

    @Test
    fun `a witness URI and a trace URI inside the workspace are rewritten to the client's`() = runTest {
        val witnessUri = "file:/abs/sessions/x/.artifacts/Case/witness.semantifyr"
        val traceUri = "file:/abs/sessions/x/.artifacts/Case/trace.get"
        val raw = VerificationCaseResult(
            "failed",
            "counterexample",
            "smart",
            null,
            witnessUri,
            traceUri,
        )
        val fixture = Fixture(raw)
        whenever(fixture.documents.toClientUri(witnessUri)).thenReturn("file:///workspace/.artifacts/Case/witness.semantifyr")
        whenever(fixture.documents.toClientUri(traceUri)).thenReturn("file:///workspace/.artifacts/Case/trace.get")

        val result = executor.execute(fixture.requestManager, fixture.documents, noArgs()) as VerificationCaseResult
        assertThat(result.status()).isEqualTo("failed")
        assertThat(result.witnessUri()).isEqualTo("file:///workspace/.artifacts/Case/witness.semantifyr")
        assertThat(result.traceUri()).isEqualTo("file:///workspace/.artifacts/Case/trace.get")
    }

    @Test
    fun `the witness a validation by case is given reaches the language server at its server URI`() = runTest {
        val fixture = Fixture("ignored")
        val clientWitnessUri = "file:///workspace/.artifacts/Case/witness.semantifyr"
        val serverWitnessUri = "file:///abs/sessions/x/.artifacts/Case/witness.semantifyr"
        whenever(fixture.documents.clientToServer(clientWitnessUri)).thenReturn(serverWitnessUri)
        val request = WitnessValidationRequest("file:///workspace/snippet.gamma", null, "smart", clientWitnessUri)

        executor.execute(fixture.requestManager, fixture.documents, ExecuteCommandParams("gamma.witness.validate", listOf<Any>(CommandGson.INSTANCE.toJsonTree(request))))

        val forwarded = argumentCaptor<ExecuteCommandParams>()
        verify(fixture.requestManager).executeCommand(forwarded.capture())
        val forwardedRequest = CommandGson.INSTANCE.fromJson(forwarded.firstValue.arguments.single() as JsonElement, WitnessValidationRequest::class.java)
        assertThat(forwardedRequest).isEqualTo(WitnessValidationRequest("file:///workspace/snippet.gamma", null, "smart", serverWitnessUri))
    }

    @Test
    fun `a witness URI outside the workspace is left untouched`() = runTest {
        val outsideUri = "file:///tmp/elsewhere/witness.semantifyr"
        val raw = VerificationCaseResult(
            "failed",
            "counterexample",
            "smart",
            null,
            outsideUri,
            null,
        )
        val fixture = Fixture(raw)
        whenever(fixture.documents.toClientUri(outsideUri)).thenReturn(outsideUri)

        assertThat(executor.execute(fixture.requestManager, fixture.documents, noArgs())).isEqualTo(raw)
    }

    @Test
    fun `an on-disk-but-untracked target document is pre-opened before the command runs`() = runTest {
        val fixture = Fixture("ignored").apply { runWritesInline() }
        val uri = "file:///workspace/.artifacts/Case/witness.semantifyr"
        whenever(fixture.documents.find(uri)).thenReturn(null)
        whenever(fixture.documents.existsOnDisk(uri)).thenReturn(true)
        whenever(fixture.documents.openExistingByClient(uri)).thenReturn(mock<SessionDocument>())

        executor.execute(fixture.requestManager, fixture.documents, argsWithUri(uri))

        verify(fixture.documents).openExistingByClient(uri)
    }

    @Test
    fun `an already-tracked target document is not re-opened`() = runTest {
        val fixture = Fixture("ignored")
        val uri = "file:///workspace/snippet.semantifyr"
        whenever(fixture.documents.find(uri)).thenReturn(mock<SessionDocument>())

        executor.execute(fixture.requestManager, fixture.documents, argsWithUri(uri))

        verify(fixture.documents, never()).openExistingByClient(any())
    }
}
