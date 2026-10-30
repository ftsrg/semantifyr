/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.ide;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import hu.bme.mit.semantifyr.frontends.gamma.lang.GammaRuntimeModule;
import hu.bme.mit.semantifyr.frontends.gamma.lang.GammaStandaloneSetup;
import hu.bme.mit.semantifyr.frontends.gamma.lang.ide.server.GammaServerModule;
import hu.bme.mit.semantifyr.lang.ide.LanguageServerLauncher;
import hu.bme.mit.semantifyr.lang.ide.server.OxstsInjector;
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.xtext.util.Modules2;

public class GammaIdeSetup extends GammaStandaloneSetup {

    private final Injector oxstsInjector;

    public GammaIdeSetup(Injector oxstsInjector) {
        this.oxstsInjector = oxstsInjector;
    }

    @Override
    public Injector createInjector() {
        var oxstsHandle = new OxstsInjector(oxstsInjector);
        return Guice.createInjector(Modules2.mixin(
                new GammaServerModule(), new GammaRuntimeModule(), new GammaIdeModule(), new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(OxstsInjector.class).toInstance(oxstsHandle);
                    }
                }));
    }

    static void main(String[] args) throws InterruptedException, ExecutionException, IOException {
        var oxstsInjector = new OxstsStandaloneSetup().createInjectorAndDoEMFRegistration();
        var injector = new GammaIdeSetup(oxstsInjector).createInjectorAndDoEMFRegistration();
        LanguageServerLauncher.launch(injector, LanguageClient.class, args);
    }
}
