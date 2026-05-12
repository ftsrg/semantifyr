/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.language

import com.google.inject.Singleton
import hu.bme.mit.semantifyr.live.backend.Flavor
import hu.bme.mit.semantifyr.live.backend.Language

interface LanguageServiceRegistry {
    fun forFlavor(flavor: Flavor): LanguageServices
}

@Singleton
class LiveLanguageServiceRegistry : LanguageServiceRegistry {

    private val oxstsInjector by lazy {
        LiveOxstsLanguageSetup().createInjectorAndDoEMFRegistration()
    }

    private val oxsts by lazy {
        oxstsInjector.getInstance(LanguageServices::class.java)
    }

    private val gammaInjector by lazy {
        LiveGammaLanguageSetup().createInjectorAndDoEMFRegistration()
    }

    private val gamma by lazy {
        gammaInjector.getInstance(LanguageServices::class.java)
    }

    override fun forFlavor(flavor: Flavor): LanguageServices {
        return when (flavor.language) {
            Language.Oxsts -> oxsts
            Language.Gamma -> gamma
        }
    }
}
