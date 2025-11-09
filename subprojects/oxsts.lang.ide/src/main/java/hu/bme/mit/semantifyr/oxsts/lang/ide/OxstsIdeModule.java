/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide;

import hu.bme.mit.semantifyr.oxsts.lang.ide.editor.contentassist.FuzzyMatcher;
import hu.bme.mit.semantifyr.oxsts.lang.ide.editor.contentassist.OxstsIdeContentProposalProvider;
import hu.bme.mit.semantifyr.oxsts.lang.ide.editor.quickfix.OxstsQuickFixProvider;
import hu.bme.mit.semantifyr.oxsts.lang.ide.editor.syntaxcoloring.OxstsSemanticHighlightingCalculator;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.codelens.OxstsCodeLensProvider;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands.OxstsCommandService;
import org.eclipse.xtext.ide.editor.contentassist.IPrefixMatcher;
import org.eclipse.xtext.ide.editor.contentassist.IdeContentProposalProvider;
import org.eclipse.xtext.ide.editor.quickfix.IQuickFixProvider;
import org.eclipse.xtext.ide.editor.syntaxcoloring.ISemanticHighlightingCalculator;
import org.eclipse.xtext.ide.server.codeActions.ICodeActionService2;
import org.eclipse.xtext.ide.server.codeActions.QuickFixCodeActionService;
import org.eclipse.xtext.ide.server.codelens.ICodeLensResolver;
import org.eclipse.xtext.ide.server.codelens.ICodeLensService;
import org.eclipse.xtext.ide.server.commands.IExecutableCommandService;

/**
 * Use this class to register ide components.
 */
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
    public Class<? extends ICodeActionService2> bindICodeActionService2() {
        return QuickFixCodeActionService.class;
    }

}
