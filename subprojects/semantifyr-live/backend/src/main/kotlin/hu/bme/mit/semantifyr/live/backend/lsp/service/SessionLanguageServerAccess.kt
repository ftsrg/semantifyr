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
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionScoped
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
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
        return client
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
        return InitializeResult(languageServices.serverCapabilities)
    }

    private fun contextFor(uri: String): Context {
        val file = sessionDocumentManager.find(uri) ?: error("No resource registered for uri=$uri")
        return Context(file.resource, file.xtextDocument(), true, cancelIndicator)
    }
}

@SessionScoped
class SessionLanguageServerAccessFactory @Inject constructor(
    private val sessionDocumentManager: SessionDocumentManager,
    private val sessionClient: SessionClient,
    private val languageServices: LanguageServices,
) {
    fun create(cancelIndicator: CancelIndicator): SessionLanguageServerAccess {
        return SessionLanguageServerAccess(
            sessionDocumentManager,
            sessionClient.get(),
            languageServices,
            cancelIndicator,
        )
    }
}
