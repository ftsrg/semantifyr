/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.ide;

import com.google.inject.Guice;
import com.google.inject.Injector;
import hu.bme.mit.semantifyr.frontends.gamma.lang.GammaRuntimeModule;
import hu.bme.mit.semantifyr.frontends.gamma.lang.GammaStandaloneSetup;
import hu.bme.mit.semantifyr.frontends.gamma.lang.ide.server.GammaServerModule;
import hu.bme.mit.semantifyr.lang.ide.LanguageServerLauncher;
import hu.bme.mit.semantifyr.lang.ide.LspCliOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.xtext.util.Modules2;

public class GammaIdeSetup extends GammaStandaloneSetup {

    private final LspCliOptions cliOptions;

    public GammaIdeSetup(LspCliOptions cliOptions) {
        this.cliOptions = cliOptions;
    }

    @Override
    public Injector createInjector() {
        return Guice.createInjector(Modules2.mixin(
                new GammaServerModule(), new GammaRuntimeModule(), new GammaIdeModule(), cliOptions.asModule()));
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException, IOException {
        Files.deleteIfExists(Path.of("gamma.lsp.log"));
        var parsed = LspCliOptions.parse(args);
        var injector = new GammaIdeSetup(parsed.options()).createInjectorAndDoEMFRegistration();
        LanguageServerLauncher.launch(injector, LanguageClient.class, parsed.remaining());
    }
}
