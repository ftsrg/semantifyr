/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.ide.server;

import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.semantifyr.frontends.gamma.lang.ide.GammaIdeSetup;
import hu.bme.mit.semantifyr.lang.ide.server.formatting.SemantifyrFormattingService;
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup;
import org.eclipse.xtext.ide.server.formatting.FormattingService;
import org.junit.jupiter.api.Test;

class GammaFormattingServiceTest {

    @Test
    void gammaFormatsRangesWithTheSharedFormattingService() {
        var oxstsInjector = new OxstsStandaloneSetup().createInjectorAndDoEMFRegistration();
        var injector = new GammaIdeSetup(oxstsInjector).createInjectorAndDoEMFRegistration();

        assertThat(injector.getInstance(FormattingService.class)).isInstanceOf(SemantifyrFormattingService.class);
    }
}
