/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.language

import com.google.inject.Injector
import hu.bme.mit.semantifyr.oxsts.lang.OxstsRuntimeModule
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsIdeModule
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.OxstsServerModule
import org.eclipse.xtext.util.Modules2

class LiveOxstsLanguageSetup(
    private val parentInjector: Injector,
) : OxstsStandaloneSetup() {

    override fun createInjector(): Injector {
        return parentInjector.createChildInjector(
            Modules2.mixin(
                OxstsServerModule(),
                OxstsRuntimeModule(),
                OxstsIdeModule(),
                LiveLanguageServerModule(),
            ),
        )
    }
}
