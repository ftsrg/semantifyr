/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.language

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.lang.ide.server.hierarchy.TypeHierarchyService
import hu.bme.mit.semantifyr.lang.ide.server.inlayhints.InlayHintService
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinLibrary
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.lsp4j.CodeLensOptions
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.ExecuteCommandOptions
import org.eclipse.lsp4j.RenameOptions
import org.eclipse.lsp4j.SemanticTokensLegend
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.SignatureHelpOptions
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.xtext.ide.server.codeActions.ICodeActionService2
import org.eclipse.xtext.ide.server.codelens.ICodeLensService
import org.eclipse.xtext.ide.server.commands.IExecutableCommandService
import org.eclipse.xtext.ide.server.contentassist.ContentAssistService
import org.eclipse.xtext.ide.server.folding.FoldingRangeService
import org.eclipse.xtext.ide.server.formatting.FormattingService
import org.eclipse.xtext.ide.server.hover.IHoverService
import org.eclipse.xtext.ide.server.occurrences.IDocumentHighlightService
import org.eclipse.xtext.ide.server.rename.IRenameService2
import org.eclipse.xtext.ide.server.rename.RenameService2
import org.eclipse.xtext.ide.server.semantictokens.SemanticTokensService
import org.eclipse.xtext.ide.server.signatureHelp.ISignatureHelpService
import org.eclipse.xtext.ide.server.symbol.DocumentSymbolService
import org.eclipse.xtext.ide.server.symbol.HierarchicalDocumentSymbolService
import org.eclipse.xtext.ide.server.symbol.WorkspaceSymbolService
import org.eclipse.xtext.resource.IResourceServiceProvider
import org.eclipse.xtext.service.OperationCanceledManager

class LanguageServices @Inject constructor(
    private val resourceSetProvider: Provider<ResourceSet>,
    val operationCanceledManager: OperationCanceledManager,
    val commandService: IExecutableCommandService,
    val resourceServiceProviderRegistry: IResourceServiceProvider.Registry,
    val contentAssistService: ContentAssistService,
    val hoverService: IHoverService,
    val signatureHelpService: ISignatureHelpService,
    val documentSymbolService: HierarchicalDocumentSymbolService,
    val richDocumentSymbolService: DocumentSymbolService,
    val workspaceSymbolService: WorkspaceSymbolService,
    val documentHighlightService: IDocumentHighlightService,
    val foldingRangeService: FoldingRangeService,
    val semanticTokensService: SemanticTokensService,
    val formattingService: FormattingService,
    val renameService: IRenameService2,
    val builtinLibrary: BuiltinLibrary,
) {

    // Xtext's stock rename reads the file from disk into a fresh resource set, which a session does not keep current.
    val supportsRename = renameService.javaClass != RenameService2::class.java

    @Inject(optional = true)
    var codeActionService: ICodeActionService2? = null

    @Inject(optional = true)
    var codeLensService: ICodeLensService? = null

    @Inject(optional = true)
    var inlayHintService: InlayHintService? = null

    @Inject(optional = true)
    var typeHierarchyService: TypeHierarchyService? = null

    fun serverCapabilities(commands: List<String>): ServerCapabilities {
        return ServerCapabilities().apply {
            textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)
            executeCommandProvider = ExecuteCommandOptions(commands)
            completionProvider = CompletionOptions(false, listOf("."))
            hoverProvider = Either.forLeft(true)
            signatureHelpProvider = SignatureHelpOptions(
                listOf(
                    "(",
                    ",",
                ),
            )
            definitionProvider = Either.forLeft(true)
            referencesProvider = Either.forLeft(true)
            documentSymbolProvider = Either.forLeft(true)
            workspaceSymbolProvider = Either.forLeft(true)
            documentHighlightProvider = Either.forLeft(true)
            documentFormattingProvider = Either.forLeft(true)
            documentRangeFormattingProvider = Either.forLeft(true)
            codeActionProvider = Either.forLeft(codeActionService != null)
            if (codeLensService != null) {
                codeLensProvider = CodeLensOptions(false)
            }
            foldingRangeProvider = Either.forLeft(true)
            if (supportsRename) {
                renameProvider = Either.forRight(RenameOptions(true))
            }
            inlayHintProvider = Either.forLeft(inlayHintService != null)
            typeHierarchyProvider = Either.forLeft(typeHierarchyService != null)
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
    }

    fun newResourceSet(): ResourceSet {
        return resourceSetProvider.get()
    }
}
