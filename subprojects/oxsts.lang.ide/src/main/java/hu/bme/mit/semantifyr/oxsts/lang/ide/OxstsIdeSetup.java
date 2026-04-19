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
import hu.bme.mit.semantifyr.oxsts.lang.ide.client.OxstsLanguageClient;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.OxstsServerModule;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.xtext.ide.server.LanguageServerImpl;
import org.eclipse.xtext.util.Modules2;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

/**
 * Initialization support for running Xtext languages as language servers.
 *
 * Three launch modes:
 * <ul>
 *     <li>{@code --network <port>} -- server opens a listening socket on {@code <port>} and waits for a client to connect.
 *         Useful for attaching a long-running debugger session.</li>
 *     <li>{@code --socket=<port>} -- server connects out to a client already listening on {@code 127.0.0.1:<port>}.
 *         Intended for VS Code's auto-port launcher: the extension binds port 0, the OS picks a free port,
 *         the extension spawns this process with that port as the arg.</li>
 *     <li>no args -- classic stdio LSP.</li>
 * </ul>
 */
public class OxstsIdeSetup extends OxstsStandaloneSetup {

    private static final String SOCKET_ARG_PREFIX = "--socket=";

    @Override
    public Injector createInjector() {
        return Guice.createInjector(Modules2.mixin(new OxstsServerModule(), new OxstsRuntimeModule(), new OxstsIdeModule()));
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException, IOException {
        Files.deleteIfExists(Path.of("oxsts.lsp.log"));

        if (args.length == 2 && args[0].equals("--network")) {
            runOnPort(Integer.parseInt(args[1]));
            return;
        }

        if (args.length == 1 && args[0].startsWith(SOCKET_ARG_PREFIX)) {
            int port = Integer.parseInt(args[0].substring(SOCKET_ARG_PREFIX.length()));
            connectToPort(port);
            return;
        }

        runOnStdIo();
    }

    private static void runOnStdIo() throws ExecutionException, InterruptedException {
        Injector injector = new OxstsIdeSetup().createInjectorAndDoEMFRegistration();
        var languageServer = injector.getInstance(LanguageServerImpl.class);

        var launcher = Launcher.createLauncher(languageServer, OxstsLanguageClient.class, System.in, System.out);
        languageServer.connect(launcher.getRemoteProxy());
        launcher.startListening().get();
    }

    private static void runOnPort(int port) throws IOException, ExecutionException, InterruptedException {
        var injector = new OxstsIdeSetup().createInjectorAndDoEMFRegistration();
        var languageServer = injector.getInstance(LanguageServerImpl.class);

        try (var serverSocket = new ServerSocket(port)) {
            System.out.println("LSP server listening on port " + port);
            try (var clientSocket = serverSocket.accept()) {
                System.out.println("Client connected from " + clientSocket.getRemoteSocketAddress());

                var in = clientSocket.getInputStream();
                var out = clientSocket.getOutputStream();

                var launcher = Launcher.createLauncher(languageServer, OxstsLanguageClient.class, in, out);
                languageServer.connect(launcher.getRemoteProxy());
                launcher.startListening().get();
            }
        }
    }

    private static void connectToPort(int port) throws IOException, ExecutionException, InterruptedException {
        var injector = new OxstsIdeSetup().createInjectorAndDoEMFRegistration();
        var languageServer = injector.getInstance(LanguageServerImpl.class);

        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port));

            var in = socket.getInputStream();
            var out = socket.getOutputStream();

            var launcher = Launcher.createLauncher(languageServer, OxstsLanguageClient.class, in, out);
            languageServer.connect(launcher.getRemoteProxy());
            launcher.startListening().get();
        }
    }

}
