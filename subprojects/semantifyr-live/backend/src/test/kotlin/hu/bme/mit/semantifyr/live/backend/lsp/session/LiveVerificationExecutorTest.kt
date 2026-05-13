/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.session

import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandGson
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationTrace
import hu.bme.mit.semantifyr.live.backend.lsp.document.SessionDocument
import hu.bme.mit.semantifyr.live.backend.lsp.document.SessionDocumentManager
import hu.bme.mit.semantifyr.live.backend.lsp.service.SessionRequestManager
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.xtext.util.CancelIndicator
import org.eclipse.xtext.xbase.lib.Functions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
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
        val requestManager = mock<SessionRequestManager>()
        val lspSession = mock<LspSession> {
            on { sessionDocumentManager } doReturn documents
            on { this.requestManager } doReturn requestManager
            on { executeCommandUnderReadLock(any()) } doReturn CompletableFuture.completedFuture(rawResult)
        }

        fun runWritesInline() {
            whenever(requestManager.runWrite<Any?, Any?>(any(), any())) doAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val nonCancellable = invocation.arguments[0] as Functions.Function0<Any?>

                @Suppress("UNCHECKED_CAST")
                val cancellable = invocation.arguments[1] as Functions.Function2<CancelIndicator, Any?, Any?>
                CompletableFuture.completedFuture(cancellable.apply(CancelIndicator.NullImpl, nonCancellable.apply()))
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
        assertThat(executor.execute(fixture.lspSession, noArgs())).isEqualTo("plain-result")
    }

    @Test
    fun `a result without a trace is returned unchanged`() = runTest {
        val raw = VerificationCaseResult("passed", "ok", "theta", "smart-full", null, null)
        val fixture = Fixture(raw)
        assertThat(executor.execute(fixture.lspSession, noArgs())).isSameAs(raw)
    }

    @Test
    fun `a witness URI inside the workspace is rewritten and the inline source dropped`() = runTest {
        val serverUri = "file:/abs/sessions/x/.artifacts/Case/witness.oxsts"
        val raw = VerificationCaseResult(
            "failed",
            "counterexample",
            "theta",
            "smart-full",
            null,
            VerificationTrace(null, null, "inline witness source", serverUri),
        )
        val fixture = Fixture(raw)
        whenever(fixture.documents.toClientUri(serverUri)).thenReturn("file:///workspace/.artifacts/Case/witness.oxsts")

        val result = executor.execute(fixture.lspSession, noArgs()) as VerificationCaseResult
        assertThat(result.status()).isEqualTo("failed")
        assertThat(result.trace().witnessUri()).isEqualTo("file:///workspace/.artifacts/Case/witness.oxsts")
        assertThat(result.trace().backAnnotatedSource()).isNull()
    }

    @Test
    fun `a witness URI outside the workspace is left untouched`() = runTest {
        val outsideUri = "file:///tmp/elsewhere/witness.oxsts"
        val raw = VerificationCaseResult(
            "failed",
            "counterexample",
            "theta",
            "smart-full",
            null,
            VerificationTrace(null, null, "inline source", outsideUri),
        )
        val fixture = Fixture(raw)
        whenever(fixture.documents.toClientUri(outsideUri)).thenReturn(outsideUri)

        assertThat(executor.execute(fixture.lspSession, noArgs())).isSameAs(raw)
    }

    @Test
    fun `an on-disk-but-untracked target document is pre-opened before the command runs`() = runTest {
        val fixture = Fixture("ignored").apply { runWritesInline() }
        val uri = "file:///workspace/.artifacts/Case/witness.oxsts"
        whenever(fixture.documents.find(uri)).thenReturn(null)
        whenever(fixture.documents.existsOnDisk(uri)).thenReturn(true)
        whenever(fixture.documents.openExistingByClient(uri)).thenReturn(mock<SessionDocument>())

        executor.execute(fixture.lspSession, argsWithUri(uri))

        verify(fixture.documents).openExistingByClient(uri)
    }

    @Test
    fun `an already-tracked target document is not re-opened`() = runTest {
        val fixture = Fixture("ignored")
        val uri = "file:///workspace/snippet.oxsts"
        whenever(fixture.documents.find(uri)).thenReturn(mock<SessionDocument>())

        executor.execute(fixture.lspSession, argsWithUri(uri))

        verify(fixture.documents, never()).openExistingByClient(any())
    }
}
