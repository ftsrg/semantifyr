/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.language

import hu.bme.mit.semantifyr.live.backend.testing.emptyGlobalsModule
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LiveGammaLanguageSetupTest {

    @Test
    fun `the services built for Gamma hold the standard library`() {
        LiveOxstsLanguageSetup(emptyGlobalsModule()).createInjectorAndDoEMFRegistration()
        val oxstsInjector = OxstsStandaloneSetup().createInjectorWithoutGlobalRegistration()
        val services = LiveGammaLanguageSetup(oxstsInjector, emptyGlobalsModule())
            .createInjectorAndDoEMFRegistration()
            .getInstance(LanguageServices::class.java)

        assertThat(services.builtinLibrary.extractedLibraryPath.resolve("semantifyr.oxsts")).isRegularFile()
    }
}
