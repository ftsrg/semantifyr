/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.ide;

import hu.bme.mit.semantifyr.frontends.gamma.lang.ide.server.commands.GammaCommandService;
import hu.bme.mit.semantifyr.lang.ide.editor.contentassist.FuzzyMatcher;
import org.eclipse.xtext.ide.editor.contentassist.IPrefixMatcher;
import org.eclipse.xtext.ide.server.commands.IExecutableCommandService;

public class GammaIdeModule extends AbstractGammaIdeModule {

    @Override
    public Class<? extends IPrefixMatcher> bindIPrefixMatcher() {
        return FuzzyMatcher.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends IExecutableCommandService> bindIExecutableCommandService() {
        return GammaCommandService.class;
    }
}
