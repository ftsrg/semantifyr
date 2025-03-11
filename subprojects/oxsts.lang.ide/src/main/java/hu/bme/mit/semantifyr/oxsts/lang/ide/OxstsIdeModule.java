/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide;

import com.google.inject.Binder;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;

/**
 * Use this class to register ide components.
 */
public class OxstsIdeModule extends AbstractOxstsIdeModule {

    @Override
    public void configure(Binder binder) {
        super.configure(binder);

        binder.bind(LanguageClientAware.class).to(OxstsLanguageServer.class);
        binder.bind(LanguageServer.class).to(OxstsLanguageServer.class);
    }

}
