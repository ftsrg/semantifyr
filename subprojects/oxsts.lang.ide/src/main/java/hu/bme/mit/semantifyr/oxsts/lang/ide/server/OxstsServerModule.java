/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

import hu.bme.mit.semantifyr.lang.ide.server.AbstractSemantifyrServerModule;
import hu.bme.mit.semantifyr.lang.ide.server.commands.SemantifyrCommandService;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands.OxstsCommandService;

public class OxstsServerModule extends AbstractSemantifyrServerModule {

    @Override
    protected void configure() {
        super.configure();

        bind(SemantifyrCommandService.class).to(OxstsCommandService.class);
    }
}
