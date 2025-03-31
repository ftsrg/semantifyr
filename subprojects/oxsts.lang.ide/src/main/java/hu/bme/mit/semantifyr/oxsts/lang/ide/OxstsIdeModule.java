/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide;

import hu.bme.mit.semantifyr.oxsts.lang.ide.contentassist.FuzzyMatcher;
import hu.bme.mit.semantifyr.oxsts.lang.ide.contentassist.OxstsContentProposalProvider;
import org.eclipse.xtext.ide.editor.contentassist.IPrefixMatcher;
import org.eclipse.xtext.ide.editor.contentassist.IdeContentProposalProvider;

/**
 * Use this class to register ide components.
 */
public class OxstsIdeModule extends AbstractOxstsIdeModule {

    @Override
    public Class<? extends IPrefixMatcher> bindIPrefixMatcher() {
        return FuzzyMatcher.class;
    }

//    public Class<? extends IdeCrossrefProposalProvider> bindIdeCrossrefProposalProvider() {
//        return IdeCrossrefProposalProvider.class;
//    }

    public Class<? extends IdeContentProposalProvider> bindIdeContentProposalProvider() {
        return OxstsContentProposalProvider.class;
    }

}
