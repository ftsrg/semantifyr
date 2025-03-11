/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide;

import com.google.inject.Guice;
import com.google.inject.Injector;
import hu.bme.mit.semantifyr.oxsts.lang.OxstsRuntimeModule;
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsPackage;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.xtext.ide.server.ServerModule;
import org.eclipse.xtext.util.Modules2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutionException;

/**
 * Initialization support for running Xtext languages as language servers.
 */
public class OxstsIdeSetup extends OxstsStandaloneSetup {

	@Override
	public Injector createInjector() {
		return Guice.createInjector(Modules2.mixin(new ServerModule(), new OxstsRuntimeModule(), new OxstsIdeModule()));
	}

    public static void main(String[] args) throws InterruptedException, ExecutionException, IOException {
        var injector = new OxstsIdeSetup().createInjectorAndDoEMFRegistration();
        OxstsPackage.eINSTANCE.getName();

        if (args.length == 2) {
            if (args[0].equals("--network")) {
                int port = Integer.parseInt(args[1]);
                runOnPort(injector, port);
                return;
            }
        }

        runOnStdIo(injector);
    }

    private static void runOnStdIo(Injector injector) throws ExecutionException, InterruptedException {
        LanguageClientAware languageServer = injector.getInstance(LanguageClientAware.class);

        Launcher<LanguageClient> launcher = Launcher.createLauncher(languageServer, LanguageClient.class, System.in, System.out);
        languageServer.connect(launcher.getRemoteProxy());
        launcher.startListening().get();
    }

    private static void runOnPort(Injector injector, int port) throws IOException, ExecutionException, InterruptedException {
        LanguageClientAware languageServer = injector.getInstance(LanguageClientAware.class);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("LSP server listening on port " + port);
            try (Socket clientSocket = serverSocket.accept()) {
                System.out.println("Client connected from " + clientSocket.getRemoteSocketAddress());

                InputStream in = clientSocket.getInputStream();
                OutputStream out = clientSocket.getOutputStream();

                Launcher<LanguageClient> launcher = Launcher.createLauncher(languageServer, LanguageClient.class, in, out);
                languageServer.connect(launcher.getRemoteProxy());
                launcher.startListening().get();
            }
        }
    }

}
