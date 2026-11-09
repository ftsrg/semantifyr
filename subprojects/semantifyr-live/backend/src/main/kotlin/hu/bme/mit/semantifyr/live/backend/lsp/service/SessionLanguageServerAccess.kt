/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.service

import com.google.inject.Inject
import hu.bme.mit.semantifyr.live.backend.lsp.document.SessionDocumentManager
import hu.bme.mit.semantifyr.live.backend.lsp.language.LanguageServices
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionClient
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionContext
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionScoped
import hu.bme.mit.semantifyr.oxsts.lang.ide.client.NavigateToParams
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.xtext.ide.server.ILanguageServerAccess
import org.eclipse.xtext.ide.server.ILanguageServerAccess.Context
import org.eclipse.xtext.ide.server.ILanguageServerAccess.IBuildListener
import org.eclipse.xtext.ide.server.ILanguageServerAccess.IndexContext
import org.eclipse.xtext.resource.impl.ResourceDescriptionsData
import org.eclipse.xtext.util.CancelIndicator
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import org.eclipse.emf.common.util.URI as EmfURI

class SessionLanguageServerAccess(
    private val sessionDocumentManager: SessionDocumentManager,
    private val client: LanguageClient,
    private val languageServices: LanguageServices,
    private val commands: List<String>,
    private val cancelIndicator: CancelIndicator,
) : ILanguageServerAccess {

    override fun <T : Any> doRead(uri: String, function: Function<Context, T>): CompletableFuture<T> {
        return CompletableFuture.completedFuture(function.apply(contextFor(uri)))
    }

    override fun <T : Any> doSyncRead(uri: String, function: Function<Context, T>): T {
        return function.apply(contextFor(uri))
    }

    override fun <T : Any> doReadIndex(function: Function<in IndexContext, out T>): CompletableFuture<T> {
        val empty = ResourceDescriptionsData(emptyList())
        return CompletableFuture.completedFuture(function.apply(IndexContext(empty, cancelIndicator)))
    }

    override fun addBuildListener(listener: IBuildListener) {
        // no build pipeline in the embedded layer
    }

    override fun getLanguageClient(): LanguageClient {
        if (client !is SessionLanguageClient) {
            return client
        }
        return ClientUriLanguageClient(client, sessionDocumentManager)
    }

    override fun newLiveScopeResourceSet(uri: EmfURI?): ResourceSet {
        return languageServices.newResourceSet()
    }

    override fun getInitializeParams(): InitializeParams {
        return InitializeParams().apply {
            rootUri = sessionDocumentManager.workspaceRoot.toUri().toString()
        }
    }

    override fun getInitializeResult(): InitializeResult {
        return InitializeResult(languageServices.serverCapabilities(commands))
    }

    private fun contextFor(uri: String): Context {
        val file = sessionDocumentManager.find(uri) ?: error("No resource registered for uri=$uri")
        return Context(file.resource, file.xtextDocument(), true, cancelIndicator)
    }
}

private class ClientUriLanguageClient(
    private val delegate: SessionLanguageClient,
    private val sessionDocumentManager: SessionDocumentManager,
) : SessionLanguageClient by delegate {

    override fun navigateTo(params: NavigateToParams): CompletableFuture<Void>? {
        val locations = params.locations().map {
            Location(sessionDocumentManager.toClientUri(it.uri), it.range)
        }
        return delegate.navigateTo(NavigateToParams(locations))
    }
}

@SessionScoped
class SessionLanguageServerAccessFactory @Inject constructor(
    private val sessionContext: SessionContext,
    private val sessionDocumentManager: SessionDocumentManager,
    private val sessionClient: SessionClient,
    private val languageServices: LanguageServices,
) {
    fun create(cancelIndicator: CancelIndicator): SessionLanguageServerAccess {
        return SessionLanguageServerAccess(
            sessionDocumentManager,
            sessionClient.get(),
            languageServices,
            sessionContext.flavor.commands,
            cancelIndicator,
        )
    }
}
