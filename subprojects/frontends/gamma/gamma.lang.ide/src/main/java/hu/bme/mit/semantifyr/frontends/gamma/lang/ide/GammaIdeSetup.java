/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.ide;

import com.google.inject.Guice;
import com.google.inject.Injector;
import hu.bme.mit.semantifyr.frontends.gamma.lang.GammaRuntimeModule;
import hu.bme.mit.semantifyr.frontends.gamma.lang.GammaStandaloneSetup;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.xtext.ide.server.LanguageServerImpl;
import org.eclipse.xtext.ide.server.ServerModule;
import org.eclipse.xtext.util.Modules2;

import java.util.concurrent.ExecutionException;

/**
 * Initialization support for running Xtext languages as language servers.
 */
public class GammaIdeSetup extends GammaStandaloneSetup {

	@Override
	public Injector createInjector() {
		return Guice.createInjector(Modules2.mixin(new GammaRuntimeModule(), new GammaIdeModule()));
	}

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        GammaIdeSetup.doSetup();

        Injector injector = Guice.createInjector(new ServerModule());
        LanguageServerImpl languageServer = injector.getInstance(LanguageServerImpl.class);

        Launcher<LanguageClient> launcher = Launcher.createLauncher(languageServer, LanguageClient.class, System.in, System.out);
        languageServer.connect(launcher.getRemoteProxy());
        launcher.startListening().get();
    }
	
}
