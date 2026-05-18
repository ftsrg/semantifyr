/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.ide.server;

import hu.bme.mit.semantifyr.frontends.gamma.lang.ide.server.commands.GammaCommandService;
import hu.bme.mit.semantifyr.lang.ide.server.AbstractSemantifyrServerModule;
import hu.bme.mit.semantifyr.lang.ide.server.commands.SemantifyrCommandService;

public class GammaServerModule extends AbstractSemantifyrServerModule {

    @Override
    protected void configure() {
        super.configure();

        bind(SemantifyrCommandService.class).to(GammaCommandService.class);
    }
}
