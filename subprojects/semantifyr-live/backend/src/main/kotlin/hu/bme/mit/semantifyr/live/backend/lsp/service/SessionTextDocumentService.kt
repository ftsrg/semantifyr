/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.service

import hu.bme.mit.semantifyr.live.backend.exceptions.WorkspaceUriException
import hu.bme.mit.semantifyr.live.backend.lsp.document.SessionDocument
import hu.bme.mit.semantifyr.live.backend.lsp.document.SessionDocumentManager
import hu.bme.mit.semantifyr.live.backend.lsp.language.LanguageServices
import hu.bme.mit.semantifyr.live.backend.lsp.session.LspSession
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.DocumentRangeFormattingParams
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.FoldingRange
import org.eclipse.lsp4j.FoldingRangeRequestParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.xtext.ide.server.codeActions.ICodeActionService2
import org.eclipse.xtext.resource.IResourceDescriptions
import org.eclipse.xtext.resource.impl.ResourceDescriptionsData
import org.eclipse.xtext.util.CancelIndicator
import java.util.concurrent.CompletableFuture

class SessionTextDocumentService(
    private val lspSession: LspSession,
    private val sessionDocumentManager: SessionDocumentManager,
    private val languageServices: LanguageServices,
) : TextDocumentService {

    private val logger by loggerFactory()

    override fun didOpen(params: DidOpenTextDocumentParams) {
        val clientUri = params.textDocument.uri
        lspSession.requestManager.runWrite {
            try {
                if (sessionDocumentManager.existsOnDisk(clientUri)) {
                    sessionDocumentManager.openExistingByClient(clientUri)
                } else {
                    sessionDocumentManager.openByClient(clientUri, params.textDocument.text)
                }
                sessionDocumentManager.validateAll(lspSession.client(), it)
            } catch (e: WorkspaceUriException) {
                logger.warn(e) { "Rejected didOpen sessionId=${lspSession.sessionId} clientUri=$clientUri" }
            }
        }
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val document = sessionDocumentManager.findByClient(params.textDocument.uri)

        if (document == null) {
            logger.warn { "didChange for unknown file sessionId=${lspSession.sessionId} clientUri=${params.textDocument.uri}" }
            return
        }

        lspSession.requestManager.runWrite {
            document.applyChanges(params.contentChanges)
            sessionDocumentManager.validateAll(lspSession.client(), it)
        }
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        sessionDocumentManager.closeByClient(params.textDocument.uri)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        // nothing to do
    }

    override fun completion(
        params: CompletionParams,
    ): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
        return readDocument(params.textDocument.uri, Either.forRight(CompletionList())) { document, cancelIndicator ->
            val list = languageServices.contentAssistService.createCompletionList(
                document.xtextDocument(),
                document.resource,
                params,
                cancelIndicator,
            )
            Either.forRight(list)
        }
    }

    override fun hover(params: HoverParams): CompletableFuture<Hover?> {
        return readDocument(params.textDocument.uri, null) { file, cancelIndicator ->
            languageServices.hoverService.hover(file.xtextDocument(), file.resource, params, cancelIndicator)
        }
    }

    override fun definition(
        params: DefinitionParams,
    ): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
        return readDocument(params.textDocument.uri, Either.forLeft(mutableListOf())) { file, cancelIndicator ->
            val raw = languageServices.richDocumentSymbolService.getDefinitions(
                file.xtextDocument(),
                file.resource,
                params,
                sessionDocumentManager.referenceResourceAccess(),
                cancelIndicator,
            ).orEmpty()
            val translated = raw.map {
                toClientLocation(it)
            }.toMutableList()
            Either.forLeft(translated)
        }
    }

    override fun references(params: ReferenceParams): CompletableFuture<MutableList<out Location>> {
        return readDocument(params.textDocument.uri, mutableListOf()) { file, cancelIndicator ->
            val emptyIndex: IResourceDescriptions = ResourceDescriptionsData(emptyList())
            val raw = languageServices.richDocumentSymbolService.getReferences(
                file.xtextDocument(),
                file.resource,
                params,
                sessionDocumentManager.referenceResourceAccess(),
                emptyIndex,
                cancelIndicator,
            ).orEmpty()
            raw.map {
                toClientLocation(it)
            }.toMutableList()
        }
    }

    override fun documentHighlight(
        params: DocumentHighlightParams,
    ): CompletableFuture<MutableList<out DocumentHighlight>> {
        return readDocument(params.textDocument.uri, mutableListOf()) { file, cancelIndicator ->
            languageServices.documentHighlightService
                .getDocumentHighlights(file.xtextDocument(), file.resource, params, cancelIndicator)
                .orEmpty()
                .toMutableList()
        }
    }

    override fun foldingRange(params: FoldingRangeRequestParams): CompletableFuture<MutableList<FoldingRange>> {
        return readDocument(params.textDocument.uri, mutableListOf()) { file, cancelIndicator ->
            languageServices.foldingRangeService
                .createFoldingRanges(file.xtextDocument(), file.resource, cancelIndicator)
                .orEmpty()
                .toMutableList()
        }
    }

    override fun documentSymbol(
        params: DocumentSymbolParams,
    ): CompletableFuture<MutableList<Either<SymbolInformation, DocumentSymbol>>> {
        return readDocument(params.textDocument.uri, mutableListOf()) { file, cancelIndicator ->
            val symbols = languageServices.documentSymbolService.getSymbols(
                file.xtextDocument(),
                file.resource,
                params,
                cancelIndicator,
            )
            symbols.map {
                Either.forRight<SymbolInformation, DocumentSymbol>(it)
            }.toMutableList()
        }
    }

    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens?> {
        return readDocument(params.textDocument.uri, null) { file, cancelIndicator ->
            languageServices.semanticTokensService.semanticTokensFull(
                file.xtextDocument(),
                file.resource,
                params,
                cancelIndicator,
            )
        }
    }

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<MutableList<out TextEdit>> {
        return readDocument(params.textDocument.uri, mutableListOf()) { file, cancelIndicator ->
            languageServices.formattingService
                .format(file.xtextDocument(), file.resource, params, cancelIndicator)
                .orEmpty()
                .toMutableList()
        }
    }

    override fun rangeFormatting(
        params: DocumentRangeFormattingParams,
    ): CompletableFuture<MutableList<out TextEdit>> {
        return readDocument(params.textDocument.uri, mutableListOf()) { file, cancelIndicator ->
            languageServices.formattingService
                .format(file.xtextDocument(), file.resource, params, cancelIndicator)
                .orEmpty()
                .toMutableList()
        }
    }

    override fun codeAction(
        params: CodeActionParams,
    ): CompletableFuture<MutableList<Either<Command, CodeAction>>> {
        val service = languageServices.codeActionService ?: return CompletableFuture.completedFuture(mutableListOf())
        return readDocument(params.textDocument.uri, mutableListOf()) { file, cancelIndicator ->
            val options = ICodeActionService2.Options().apply {
                this.codeActionParams = params
                this.cancelIndicator = cancelIndicator
                this.document = file.xtextDocument()
                this.resource = file.resource
                this.languageServerAccess = SessionLanguageServerAccess.forSession(lspSession, cancelIndicator)
            }
            service.getCodeActions(options).orEmpty().toMutableList()
        }
    }

    private inline fun <T> readDocument(
        clientUri: String,
        empty: T,
        crossinline block: (SessionDocument, CancelIndicator) -> T,
    ): CompletableFuture<T> {
        return lspSession.requestManager.runRead {
            val document = sessionDocumentManager.findByClient(clientUri)
            if (document == null) {
                return@runRead empty
            }
            block(document, it)
        }
    }

    private fun toClientLocation(location: Location): Location {
        val translated = sessionDocumentManager.serverToClient(location.uri)
        if (translated == location.uri) {
            return location
        }
        return Location(translated, location.range)
    }
}
