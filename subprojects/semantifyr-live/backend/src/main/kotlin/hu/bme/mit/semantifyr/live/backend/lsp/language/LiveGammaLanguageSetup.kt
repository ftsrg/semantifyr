/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.language

import com.google.inject.AbstractModule
import com.google.inject.Injector
import hu.bme.mit.semantifyr.frontends.gamma.lang.GammaRuntimeModule
import hu.bme.mit.semantifyr.frontends.gamma.lang.GammaStandaloneSetup
import hu.bme.mit.semantifyr.frontends.gamma.lang.ide.GammaIdeModule
import hu.bme.mit.semantifyr.frontends.gamma.lang.ide.server.GammaServerModule
import hu.bme.mit.semantifyr.lang.ide.server.OxstsInjector
import org.eclipse.xtext.util.Modules2

class LiveGammaLanguageSetup(
    private val parentInjector: Injector,
    private val oxstsInjector: Injector,
) : GammaStandaloneSetup() {

    override fun createInjector(): Injector {
        val oxstsHandle = OxstsInjector(oxstsInjector)
        return parentInjector.createChildInjector(
            Modules2.mixin(
                GammaServerModule(),
                GammaRuntimeModule(),
                GammaIdeModule(),
                LiveLanguageServerModule(),
                object : AbstractModule() {
                    override fun configure() {
                        bind(OxstsInjector::class.java).toInstance(oxstsHandle)
                    }
                },
            ),
        )
    }
}
