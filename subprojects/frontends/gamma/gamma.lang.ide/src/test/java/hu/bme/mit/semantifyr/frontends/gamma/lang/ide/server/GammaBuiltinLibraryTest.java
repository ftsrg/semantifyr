/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.ide.server;

import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.semantifyr.frontends.gamma.lang.ide.GammaIdeSetup;
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinLibrary;
import org.junit.jupiter.api.Test;

class GammaBuiltinLibraryTest {

    @Test
    void theBuiltinLibraryBuiltForGammaHoldsTheStandardLibrary() {
        var oxstsInjector = new OxstsStandaloneSetup().createInjectorAndDoEMFRegistration();
        var injector = new GammaIdeSetup(oxstsInjector).createInjectorAndDoEMFRegistration();

        var library = injector.getInstance(BuiltinLibrary.class);

        assertThat(library.getExtractedLibraryPath().resolve("standard.semantifyr"))
                .isRegularFile();
    }
}
