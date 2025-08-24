/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide;

import hu.bme.mit.semantifyr.oxsts.lang.ide.contentassist.FuzzyMatcher;
import hu.bme.mit.semantifyr.oxsts.lang.ide.syntaxcoloring.OxstsSemanticHighlightingCalculator;
import org.eclipse.xtext.ide.editor.contentassist.IPrefixMatcher;
import org.eclipse.xtext.ide.editor.syntaxcoloring.ISemanticHighlightingCalculator;

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

}
