/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.language

import com.google.inject.Inject
import com.google.inject.Injector
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.Flavor
import hu.bme.mit.semantifyr.live.backend.Language
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup

interface LanguageServiceRegistry {
    fun forFlavor(flavor: Flavor): LanguageServices
    fun injectorFor(flavor: Flavor): Injector
}

@Singleton
class LiveLanguageServiceRegistry @Inject constructor(
    parentInjector: Injector,
) : LanguageServiceRegistry {

    private val oxstsInjector = LiveOxstsLanguageSetup(parentInjector).createInjectorAndDoEMFRegistration()

    // injector to be used from the Frontends
    private val plainOxstsInjector = OxstsStandaloneSetup().createInjectorWithoutGlobalRegistration()

    private val gammaInjector = LiveGammaLanguageSetup(parentInjector, plainOxstsInjector).createInjectorAndDoEMFRegistration()

    private val oxsts = oxstsInjector.getInstance(LanguageServices::class.java)
    private val gamma = gammaInjector.getInstance(LanguageServices::class.java)

    override fun forFlavor(flavor: Flavor): LanguageServices {
        return when (flavor.language) {
            Language.Oxsts -> oxsts
            Language.Gamma -> gamma
        }
    }

    override fun injectorFor(flavor: Flavor): Injector {
        return when (flavor.language) {
            Language.Oxsts -> oxstsInjector
            Language.Gamma -> gammaInjector
        }
    }
}
