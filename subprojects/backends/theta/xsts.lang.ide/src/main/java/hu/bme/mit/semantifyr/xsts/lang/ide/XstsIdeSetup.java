/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.xsts.lang.ide;

import com.google.inject.Guice;
import com.google.inject.Injector;
import hu.bme.mit.semantifyr.xsts.lang.XstsRuntimeModule;
import hu.bme.mit.semantifyr.xsts.lang.XstsStandaloneSetup;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.xtext.ide.server.LanguageServerImpl;
import org.eclipse.xtext.ide.server.ServerModule;
import org.eclipse.xtext.util.Modules2;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutionException;

/**
 * Initialization support for running Xtext languages as language servers.
 */
public class XstsIdeSetup extends XstsStandaloneSetup {

    @Override
    public Injector createInjector() {
        return Guice.createInjector(Modules2.mixin(new ServerModule(), new XstsRuntimeModule(), new XstsIdeModule()));
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException, IOException {
        if (args.length == 2) {
            if (args[0].equals("--network")) {
                int port = Integer.parseInt(args[1]);
                runOnPort(port);
                return;
            }
        }

        runOnStdIo();
    }

    private static void runOnStdIo() throws ExecutionException, InterruptedException {
        Injector injector = new XstsIdeSetup().createInjectorAndDoEMFRegistration();
        var languageServer = injector.getInstance(LanguageServerImpl.class);

        var launcher = Launcher.createLauncher(languageServer, LanguageClient.class, System.in, System.out);
        languageServer.connect(launcher.getRemoteProxy());
        launcher.startListening().get();
    }

    private static void runOnPort(int port) throws IOException, ExecutionException, InterruptedException {
        var injector = new XstsIdeSetup().createInjectorAndDoEMFRegistration();
        var languageServer = injector.getInstance(LanguageServerImpl.class);

        try (var serverSocket = new ServerSocket(port)) {
            System.out.println("LSP server listening on port " + port);
            try (var clientSocket = serverSocket.accept()) {
                System.out.println("Client connected from " + clientSocket.getRemoteSocketAddress());

                var in = clientSocket.getInputStream();
                var out = clientSocket.getOutputStream();

                var launcher = Launcher.createLauncher(languageServer, LanguageClient.class, in, out);
                languageServer.connect(launcher.getRemoteProxy());
                launcher.startListening().get();
            }
        }
    }

}
