/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide;

import hu.bme.mit.semantifyr.oxsts.lang.ide.codelens.OxstsCodeLensProvider;
import hu.bme.mit.semantifyr.oxsts.lang.ide.commands.OxstsCommandService;
import hu.bme.mit.semantifyr.oxsts.lang.ide.contentassist.FuzzyMatcher;
import hu.bme.mit.semantifyr.oxsts.lang.ide.syntaxcoloring.OxstsSemanticHighlightingCalculator;
import org.eclipse.xtext.ide.editor.contentassist.IPrefixMatcher;
import org.eclipse.xtext.ide.editor.syntaxcoloring.ISemanticHighlightingCalculator;
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

}
