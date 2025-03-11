/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide;

import org.eclipse.lsp4j.*;
import org.eclipse.xtext.ide.server.LanguageServerImpl;

import java.util.ArrayList;
import java.util.List;

public class OxstsLanguageServer extends LanguageServerImpl {

    @Override
    protected ServerCapabilities createServerCapabilities(InitializeParams params) {
        ServerCapabilities capabilities = super.createServerCapabilities(params);

        SemanticTokensWithRegistrationOptions options = new SemanticTokensWithRegistrationOptions();

        List<String> tokenTypes = new ArrayList<>();
        tokenTypes.add(SemanticTokenTypes.Namespace);

        List<String> tokenModifiers = new ArrayList<>();
        tokenModifiers.add(SemanticTokenModifiers.Declaration);

        options.setLegend(new SemanticTokensLegend(tokenTypes, tokenModifiers));
        options.setRange(false);
        options.setFull(true);

        capabilities.setSemanticTokensProvider(options);

        return capabilities;
    }

}
