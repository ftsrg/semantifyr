/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.lsp.UriRewriter
import hu.bme.mit.semantifyr.live.backend.lsp.WorkspaceSyncer
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspClientRawConnector
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.LspServerRawConnector
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.SessionControlManager
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.SessionInfoProvider
import hu.bme.mit.semantifyr.live.backend.lsp.bridge.SessionVerificationManager
import hu.bme.mit.semantifyr.live.backend.lsp.createLspMessageHandler
import hu.bme.mit.semantifyr.live.backend.session.LspServerRawRunner
import hu.bme.mit.semantifyr.live.backend.session.LspSession
import hu.bme.mit.semantifyr.live.backend.session.SessionContext
import hu.bme.mit.semantifyr.live.backend.session.SessionScope
import hu.bme.mit.semantifyr.live.backend.session.SessionScoped
import hu.bme.mit.semantifyr.live.backend.session.WebSocketLspClientRawConnector
import hu.bme.mit.semantifyr.live.backend.session.seededKeyProvider
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler

private const val CLIENT_URI = "file:///workspace/"

class BackendModule(
    private val config: BackendConfig,
) : AbstractModule() {

    override fun configure() {
        bind(BackendConfig::class.java).toInstance(config)

        bindScope(SessionScoped::class.java, SessionScope)

        bind(SessionContext::class.java).toProvider(seededKeyProvider()).`in`(SessionScoped::class.java)

        bind(SessionInfoProvider::class.java).to(LspSession::class.java)
        bind(SessionVerificationManager::class.java).to(LspSession::class.java)
        bind(SessionControlManager::class.java).to(LspSession::class.java)
        bind(LspServerRawConnector::class.java).to(LspServerRawRunner::class.java)
        bind(LspClientRawConnector::class.java).to(WebSocketLspClientRawConnector::class.java)
    }

    @Provides
    @SessionScoped
    fun provideUriRewriter(context: SessionContext): UriRewriter {
        val rawServerUri = context.workingDirectoryPath.toUri().toString()
        val serverUri = if (rawServerUri.endsWith("/")) rawServerUri else "$rawServerUri/"
        return UriRewriter(clientUri = CLIENT_URI, serverUri = serverUri)
    }

    @Provides
    @SessionScoped
    fun provideWorkspaceSyncer(context: SessionContext): WorkspaceSyncer {
        return WorkspaceSyncer(
            sessionId = context.sessionId,
            clientUri = CLIENT_URI,
            targetFile = context.workingDirectoryPath.resolve(context.flavor.fileName),
            verificationCommand = context.flavor.verificationCommand,
        )
    }

    @Provides
    @Singleton
    fun provideLspMessageHandler(): MessageJsonHandler {
        return createLspMessageHandler()
    }
}
