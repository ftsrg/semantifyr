/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.service

import com.google.gson.JsonElement
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandGson
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest
import hu.bme.mit.semantifyr.live.backend.data.VerificationKind
import hu.bme.mit.semantifyr.live.backend.lsp.document.SessionDocumentManager
import hu.bme.mit.semantifyr.live.backend.lsp.session.LspSession
import hu.bme.mit.semantifyr.live.backend.lsp.session.VerificationExecutor
import hu.bme.mit.semantifyr.live.backend.lsp.session.VerificationManager
import hu.bme.mit.semantifyr.live.backend.testing.LspWire
import hu.bme.mit.semantifyr.live.backend.testing.testFlavor
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture

class SessionWorkspaceServiceTest {

    private fun verifyArgs(
        uri: String = "file:///workspace/snippet.oxsts",
        portfolio: String? = "smart-full",
    ): JsonElement {
        return CommandGson.INSTANCE.toJsonTree(VerificationCaseRequest(uri, LspWire.range(), portfolio))
    }

    private class Fixture {
        val documentManager = mock<SessionDocumentManager>()
        val verificationManager = mock<VerificationManager>()
        val verificationExecutor = mock<VerificationExecutor> {
            onBlocking { execute(any(), any()) } doReturn "verified"
        }
        val lspSession = mock<LspSession> {
            on { flavor } doReturn testFlavor()
            on { sessionDocumentManager } doReturn documentManager
            on { executeCommandUnderReadLock(any()) } doReturn CompletableFuture.completedFuture<Any?>("command-result")
        }
        val service = SessionWorkspaceService(lspSession, verificationManager, verificationExecutor)

        fun runVerificationWorkInline() {
            whenever(verificationManager.run(any(), any(), any(), any())) doAnswer {
                @Suppress("UNCHECKED_CAST")
                val work = it.arguments[3] as suspend () -> Any?
                CompletableFuture.supplyAsync {
                    runBlocking {
                        work()
                    }
                }
            }
        }
    }

    @Test
    fun `executeCommand flushes documents to disk before dispatching`() = runTest {
        val fixture = Fixture()
        fixture.service.executeCommand(ExecuteCommandParams("oxsts.case.discover", emptyList())).await()
        verify(fixture.documentManager).flushAllToDisk()
    }

    @Test
    fun `non-throttled command goes through the session command path`() = runTest {
        val fixture = Fixture()
        val result = fixture.service.executeCommand(ExecuteCommandParams("oxsts.case.discover", emptyList())).await()
        assertThat(result).isEqualTo("command-result")
        verify(fixture.lspSession).executeCommandUnderReadLock(any())
    }

    @Test
    fun `verify command is routed through the verification manager and executor`() = runTest {
        val fixture = Fixture().apply { runVerificationWorkInline() }
        val params = ExecuteCommandParams("oxsts.case.verify", listOf<Any>(verifyArgs()))

        val result = fixture.service.executeCommand(params).await()
        assertThat(result).isEqualTo("verified")

        val requestCaptor = argumentCaptor<VerificationCaseRequest>()
        val kindCaptor = argumentCaptor<VerificationKind>()
        verify(fixture.verificationManager).run(any(), requestCaptor.capture(), kindCaptor.capture(), any())
        assertThat(requestCaptor.firstValue.portfolio()).isEqualTo("smart-full")
        assertThat(kindCaptor.firstValue).isEqualTo(VerificationKind.Verify)
        verifyBlocking(fixture.verificationExecutor) { execute(any(), any()) }
    }

    @Test
    fun `validateWitness command is classified as Validate`() = runTest {
        val fixture = Fixture().apply { runVerificationWorkInline() }
        fixture.service.executeCommand(ExecuteCommandParams("oxsts.case.validateWitness", listOf<Any>(verifyArgs()))).await()
        val kindCaptor = argumentCaptor<VerificationKind>()
        verify(fixture.verificationManager).run(any(), any(), kindCaptor.capture(), any())
        assertThat(kindCaptor.firstValue).isEqualTo(VerificationKind.Validate)
    }

    @Test
    fun `verify command without a portfolio is rejected with InvalidParams`() {
        val fixture = Fixture()
        assertThatThrownBy {
            fixture.service.executeCommand(ExecuteCommandParams("oxsts.case.verify", listOf<Any>(verifyArgs(portfolio = null))))
        }.isInstanceOf(ResponseErrorException::class.java).hasMessageContaining("portfolio")
    }

    @Test
    fun `verify command without an argument object is rejected with InvalidParams`() {
        val fixture = Fixture()
        assertThatThrownBy {
            fixture.service.executeCommand(ExecuteCommandParams("oxsts.case.verify", emptyList()))
        }.isInstanceOf(ResponseErrorException::class.java)
    }
}
