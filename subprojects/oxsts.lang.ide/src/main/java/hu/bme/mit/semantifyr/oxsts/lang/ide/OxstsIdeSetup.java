/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide;

import com.google.inject.Guice;
import com.google.inject.Injector;
import hu.bme.mit.semantifyr.lang.ide.LanguageServerLauncher;
import hu.bme.mit.semantifyr.oxsts.lang.OxstsRuntimeModule;
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup;
import hu.bme.mit.semantifyr.oxsts.lang.ide.client.SemantifyrLanguageClient;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.OxstsServerModule;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.eclipse.xtext.util.Modules2;

public class OxstsIdeSetup extends OxstsStandaloneSetup {

    @Override
    public Injector createInjector() {
        return Guice.createInjector(
                Modules2.mixin(new OxstsServerModule(), new OxstsRuntimeModule(), new OxstsIdeModule()));
    }

    static void main(String[] args) throws InterruptedException, ExecutionException, IOException {
        var injector = new OxstsIdeSetup().createInjectorAndDoEMFRegistration();
        LanguageServerLauncher.launch(injector, SemantifyrLanguageClient.class, args);
    }
}
