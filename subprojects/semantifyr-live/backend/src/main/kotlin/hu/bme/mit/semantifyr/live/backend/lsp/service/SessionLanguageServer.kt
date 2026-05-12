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
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.WorkDoneProgressCancelParams
import org.eclipse.lsp4j.jsonrpc.Endpoint
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture

class SessionLanguageServer(
    private val lspSession: LspSession,
    sessionDocumentManager: SessionDocumentManager,
    verificationManager: VerificationManager,
    verificationExecutor: VerificationExecutor,
    private val languageServices: LanguageServices,
    private val workManager: WorkManager,
) : LanguageServer,
    LanguageClientAware {

    private val textDocumentService = SessionTextDocumentService(lspSession, sessionDocumentManager, languageServices)
    private val workspaceService = SessionWorkspaceService(lspSession, verificationManager, verificationExecutor)
    private val extensions = SessionSemantifyrExtensions(lspSession, verificationManager)

    private val services = listOf(this, extensions)

    fun toEndpoint(): Endpoint {
        return ServiceEndpoints.toEndpoint(services)
    }

    fun supportedMethods(): Map<String, JsonRpcMethod> {
        return buildMap {
            for (service in services) {
                putAll(ServiceEndpoints.getSupportedMethods(service.javaClass))
            }
        }
    }

    override fun connect(client: LanguageClient) {
        lspSession.attachClient(client)
        workManager.initialize(client)
    }

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        return CompletableFuture.completedFuture(InitializeResult(languageServices.serverCapabilities))
    }

    override fun shutdown(): CompletableFuture<Any> {
        return CompletableFuture.completedFuture(Unit)
    }

    override fun exit() {
        // no op
    }

    override fun getTextDocumentService(): TextDocumentService {
        return textDocumentService
    }

    override fun getWorkspaceService(): WorkspaceService {
        return workspaceService
    }

    override fun cancelProgress(params: WorkDoneProgressCancelParams) {
        workManager.cancelProgress(params)
    }
}
