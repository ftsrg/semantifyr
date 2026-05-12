/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.language

import com.google.inject.Inject
import com.google.inject.Provider
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.ExecuteCommandOptions
import org.eclipse.lsp4j.SemanticTokensLegend
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.xtext.ide.server.codeActions.ICodeActionService2
import org.eclipse.xtext.ide.server.commands.IExecutableCommandService
import org.eclipse.xtext.ide.server.contentassist.ContentAssistService
import org.eclipse.xtext.ide.server.folding.FoldingRangeService
import org.eclipse.xtext.ide.server.formatting.FormattingService
import org.eclipse.xtext.ide.server.hover.IHoverService
import org.eclipse.xtext.ide.server.occurrences.IDocumentHighlightService
import org.eclipse.xtext.ide.server.semantictokens.SemanticTokensService
import org.eclipse.xtext.ide.server.symbol.DocumentSymbolService
import org.eclipse.xtext.ide.server.symbol.IDocumentSymbolService
import org.eclipse.xtext.resource.IResourceServiceProvider
import org.eclipse.xtext.service.OperationCanceledManager

class LanguageServices @Inject constructor(
    private val resourceSetProvider: Provider<ResourceSet>,
    val operationCanceledManager: OperationCanceledManager,
    val commandService: IExecutableCommandService,
    val resourceServiceProviderRegistry: IResourceServiceProvider.Registry,
    val contentAssistService: ContentAssistService,
    val hoverService: IHoverService,
    val documentSymbolService: IDocumentSymbolService,
    val richDocumentSymbolService: DocumentSymbolService,
    val documentHighlightService: IDocumentHighlightService,
    val foldingRangeService: FoldingRangeService,
    val semanticTokensService: SemanticTokensService,
    val formattingService: FormattingService,
) {

    @Inject(optional = true)
    var codeActionService: ICodeActionService2? = null

    val serverCapabilities = ServerCapabilities().apply {
        textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)
        executeCommandProvider = ExecuteCommandOptions(commandService.initialize())
        completionProvider = CompletionOptions(false, listOf("."))
        hoverProvider = Either.forLeft(true)
        definitionProvider = Either.forLeft(true)
        referencesProvider = Either.forLeft(true)
        documentSymbolProvider = Either.forLeft(true)
        documentHighlightProvider = Either.forLeft(true)
        documentFormattingProvider = Either.forLeft(true)
        documentRangeFormattingProvider = Either.forLeft(true)
        codeActionProvider = Either.forLeft(codeActionService != null)
        foldingRangeProvider = Either.forLeft(true)
        semanticTokensProvider = SemanticTokensWithRegistrationOptions(
            SemanticTokensLegend(
                semanticTokensService.tokenTypes,
                semanticTokensService.tokenModifiers,
            ),
        ).apply {
            full = Either.forLeft(true)
            range = Either.forLeft(false)
        }
    }

    fun newResourceSet(): ResourceSet {
        return resourceSetProvider.get()
    }

}
