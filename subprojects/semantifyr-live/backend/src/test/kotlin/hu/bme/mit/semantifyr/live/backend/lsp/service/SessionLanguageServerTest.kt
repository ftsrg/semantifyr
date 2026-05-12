/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.service

import hu.bme.mit.semantifyr.lang.ide.server.concurrent.WorkManager
import hu.bme.mit.semantifyr.live.backend.lsp.document.SessionDocumentManager
import hu.bme.mit.semantifyr.live.backend.lsp.language.LanguageServices
import hu.bme.mit.semantifyr.live.backend.lsp.session.LspSession
import hu.bme.mit.semantifyr.live.backend.lsp.session.VerificationExecutor
import hu.bme.mit.semantifyr.live.backend.lsp.session.VerificationManager
import hu.bme.mit.semantifyr.live.backend.testing.testFlavor
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.WorkDoneProgressCancelParams
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class SessionLanguageServerTest {

    private val serverCapabilities = ServerCapabilities()

    private val workManager = mock<WorkManager>()
    private val languageServices = mock<LanguageServices> {
        on { this.serverCapabilities } doReturn serverCapabilities
    }
    private val lspSession = mock<LspSession> {
        on { flavor } doReturn testFlavor()
    }

    private val server = SessionLanguageServer(
        lspSession,
        mock<SessionDocumentManager>(),
        mock<VerificationManager>(),
        mock<VerificationExecutor>(),
        languageServices,
        workManager,
    )

    @Test
    fun `initialize returns the language server capabilities`() {
        val result = server.initialize(InitializeParams()).get()
        assertThat(result.capabilities).isSameAs(serverCapabilities)
    }

    @Test
    fun `shutdown completes and exit does not throw`() {
        assertThat(server.shutdown()).isCompleted
        server.exit()
    }

    @Test
    fun `connect attaches the client and initializes the work manager`() {
        val client = mock<LanguageClient>()
        server.connect(client)
        verify(lspSession).attachClient(client)
        verify(workManager).initialize(client)
    }

    @Test
    fun `cancelProgress forwards to the work manager`() {
        val params = WorkDoneProgressCancelParams()
        server.cancelProgress(params)
        verify(workManager).cancelProgress(params)
    }

    @Test
    fun `supportedMethods includes the standard and semantifyr-live methods`() {
        val methods = server.supportedMethods()
        assertThat(methods).containsKeys("initialize", "workspace/executeCommand", "semantifyr/live/session/info")
    }

    @Test
    fun `toEndpoint and the delegated services are available`() {
        assertThat(server.toEndpoint()).isNotNull
        assertThat(server.textDocumentService).isNotNull
        assertThat(server.workspaceService).isNotNull
    }
}
