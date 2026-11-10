/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide;

import hu.bme.mit.semantifyr.lang.ide.editor.contentassist.FuzzyMatcher;
import hu.bme.mit.semantifyr.lang.ide.server.callhierarchy.CallHierarchyService;
import hu.bme.mit.semantifyr.lang.ide.server.formatting.SemantifyrFormattingService;
import hu.bme.mit.semantifyr.lang.ide.server.hierarchy.TypeHierarchyService;
import hu.bme.mit.semantifyr.lang.ide.server.inlayhints.InlayHintService;
import hu.bme.mit.semantifyr.oxsts.lang.ide.editor.contentassist.OxstsIdeContentProposalProvider;
import hu.bme.mit.semantifyr.oxsts.lang.ide.editor.contentassist.OxstsIdeCrossrefProposalProvider;
import hu.bme.mit.semantifyr.oxsts.lang.ide.editor.quickfix.OxstsQuickFixProvider;
import hu.bme.mit.semantifyr.oxsts.lang.ide.editor.syntaxcoloring.OxstsSemanticHighlightingCalculator;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.callhierarchy.OxstsCallHierarchyService;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.codelens.OxstsCodeLensProvider;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands.OxstsCommandService;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.contentassist.OxstsContentAssistService;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.folding.OxstsFoldingRangeService;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.hierarchy.OxstsTypeHierarchyService;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.hover.OxstsHoverService;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.inlayhints.OxstsInlayHintService;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.occurrences.OxstsDocumentHighlightService;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.references.OxstsDocumentSymbolService;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.rename.OxstsRenameService;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.signatureHelp.OxstsSignatureHelpService;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.symbol.OxstsDocumentSymbolDetailsProvider;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.symbol.OxstsDocumentSymbolKindProvider;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.symbol.OxstsDocumentSymbolNameProvider;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.symbol.OxstsWorkspaceSymbolService;
import org.eclipse.xtext.ide.editor.contentassist.IPrefixMatcher;
import org.eclipse.xtext.ide.editor.contentassist.IdeContentProposalProvider;
import org.eclipse.xtext.ide.editor.contentassist.IdeCrossrefProposalProvider;
import org.eclipse.xtext.ide.editor.quickfix.IQuickFixProvider;
import org.eclipse.xtext.ide.editor.syntaxcoloring.ISemanticHighlightingCalculator;
import org.eclipse.xtext.ide.server.codeActions.ICodeActionService2;
import org.eclipse.xtext.ide.server.codeActions.QuickFixCodeActionService;
import org.eclipse.xtext.ide.server.codelens.ICodeLensResolver;
import org.eclipse.xtext.ide.server.codelens.ICodeLensService;
import org.eclipse.xtext.ide.server.commands.IExecutableCommandService;
import org.eclipse.xtext.ide.server.contentassist.ContentAssistService;
import org.eclipse.xtext.ide.server.folding.FoldingRangeService;
import org.eclipse.xtext.ide.server.formatting.FormattingService;
import org.eclipse.xtext.ide.server.hover.IHoverService;
import org.eclipse.xtext.ide.server.occurrences.IDocumentHighlightService;
import org.eclipse.xtext.ide.server.rename.IRenameService2;
import org.eclipse.xtext.ide.server.signatureHelp.ISignatureHelpService;
import org.eclipse.xtext.ide.server.symbol.DocumentSymbolMapper;
import org.eclipse.xtext.ide.server.symbol.DocumentSymbolService;
import org.eclipse.xtext.ide.server.symbol.WorkspaceSymbolService;

/** Use this class to register ide components. */
public class OxstsIdeModule extends AbstractOxstsIdeModule {

    @Override
    public Class<? extends IPrefixMatcher> bindIPrefixMatcher() {
        return FuzzyMatcher.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends ISemanticHighlightingCalculator> bindISemanticHighlightingCalculator() {
        return OxstsSemanticHighlightingCalculator.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends ICodeLensResolver> bindICodeLensResolver() {
        return OxstsCodeLensProvider.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends ICodeLensService> bindICodeLensService() {
        return OxstsCodeLensProvider.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends IExecutableCommandService> bindIExecutableCommandService() {
        return OxstsCommandService.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends IQuickFixProvider> bindIQuickFixProvider() {
        return OxstsQuickFixProvider.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends IdeContentProposalProvider> bindIdeContentProposalProvider() {
        return OxstsIdeContentProposalProvider.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends IdeCrossrefProposalProvider> bindIdeCrossrefProposalProvider() {
        return OxstsIdeCrossrefProposalProvider.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends ContentAssistService> bindContentAssistService() {
        return OxstsContentAssistService.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends ICodeActionService2> bindICodeActionService2() {
        return QuickFixCodeActionService.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends FormattingService> bindFormattingService() {
        return SemantifyrFormattingService.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends IHoverService> bindIHoverService() {
        return OxstsHoverService.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends ISignatureHelpService> bindISignatureHelpService() {
        return OxstsSignatureHelpService.class;
    }

    @Override
    public Class<? extends IRenameService2> bindIRenameService2() {
        return OxstsRenameService.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends DocumentSymbolService> bindDocumentSymbolService() {
        return OxstsDocumentSymbolService.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends IDocumentHighlightService> bindIDocumentHighlightService() {
        return OxstsDocumentHighlightService.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends DocumentSymbolMapper.DocumentSymbolKindProvider> bindDocumentSymbolKindProvider() {
        return OxstsDocumentSymbolKindProvider.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends DocumentSymbolMapper.DocumentSymbolNameProvider> bindDocumentSymbolNameProvider() {
        return OxstsDocumentSymbolNameProvider.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends DocumentSymbolMapper.DocumentSymbolDetailsProvider> bindDocumentSymbolDetailsProvider() {
        return OxstsDocumentSymbolDetailsProvider.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends WorkspaceSymbolService> bindWorkspaceSymbolService() {
        return OxstsWorkspaceSymbolService.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends FoldingRangeService> bindFoldingRangeService() {
        return OxstsFoldingRangeService.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends InlayHintService> bindInlayHintService() {
        return OxstsInlayHintService.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends TypeHierarchyService> bindTypeHierarchyService() {
        return OxstsTypeHierarchyService.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends CallHierarchyService> bindCallHierarchyService() {
        return OxstsCallHierarchyService.class;
    }
}
