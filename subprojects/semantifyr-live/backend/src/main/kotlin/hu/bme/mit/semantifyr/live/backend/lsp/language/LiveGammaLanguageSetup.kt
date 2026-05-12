/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.language

import com.google.inject.Guice
import com.google.inject.Injector
import hu.bme.mit.semantifyr.frontends.gamma.lang.GammaRuntimeModule
import hu.bme.mit.semantifyr.frontends.gamma.lang.GammaStandaloneSetup
import hu.bme.mit.semantifyr.frontends.gamma.lang.ide.GammaIdeModule
import hu.bme.mit.semantifyr.frontends.gamma.lang.ide.server.GammaServerModule
import org.eclipse.xtext.util.Modules2

class LiveGammaLanguageSetup : GammaStandaloneSetup() {

    override fun createInjector(): Injector {
        return Guice.createInjector(
            Modules2.mixin(
                GammaServerModule(),
                GammaRuntimeModule(),
                GammaIdeModule(),
                LiveLanguageServerModule(),
            ),
        )
    }
}
