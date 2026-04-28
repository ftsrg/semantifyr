/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide;

import com.google.inject.Injector;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.xtext.ide.server.LanguageServerImpl;

public final class LanguageServerLauncher {

    private static final String SOCKET_ARG_PREFIX = "--socket=";

    private LanguageServerLauncher() {}

    public static <T extends LanguageClient> void launch(Injector injector, Class<T> languageClientClass, String[] args)
            throws IOException, InterruptedException, ExecutionException {
        if (args.length == 2 && args[0].equals("--network")) {
            runOnPort(injector, languageClientClass, Integer.parseInt(args[1]));
            return;
        }

        if (args.length == 1 && args[0].startsWith(SOCKET_ARG_PREFIX)) {
            int port = Integer.parseInt(args[0].substring(SOCKET_ARG_PREFIX.length()));
            connectToPort(injector, languageClientClass, port);
            return;
        }

        runOnStdIo(injector, languageClientClass);
    }

    private static <T extends LanguageClient> void runOnStdIo(Injector injector, Class<T> languageClientClass)
            throws ExecutionException, InterruptedException {
        var languageServer = injector.getInstance(LanguageServerImpl.class);

        var launcher = Launcher.createLauncher(languageServer, languageClientClass, System.in, System.out);
        languageServer.connect(launcher.getRemoteProxy());
        launcher.startListening().get();
    }

    private static <T extends LanguageClient> void runOnPort(Injector injector, Class<T> languageClientClass, int port)
            throws IOException, ExecutionException, InterruptedException {
        var languageServer = injector.getInstance(LanguageServerImpl.class);

        try (var serverSocket = new ServerSocket(port)) {
            System.out.println("LSP server listening on port " + port);
            try (var clientSocket = serverSocket.accept()) {
                System.out.println("Client connected from " + clientSocket.getRemoteSocketAddress());

                var in = clientSocket.getInputStream();
                var out = clientSocket.getOutputStream();

                var launcher = Launcher.createLauncher(languageServer, languageClientClass, in, out);
                languageServer.connect(launcher.getRemoteProxy());
                launcher.startListening().get();
            }
        }
    }

    private static <T extends LanguageClient> void connectToPort(
            Injector injector, Class<T> languageClientClass, int port)
            throws IOException, ExecutionException, InterruptedException {
        var languageServer = injector.getInstance(LanguageServerImpl.class);

        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port));

            var in = socket.getInputStream();
            var out = socket.getOutputStream();

            var launcher = Launcher.createLauncher(languageServer, languageClientClass, in, out);
            languageServer.connect(launcher.getRemoteProxy());
            launcher.startListening().get();
        }
    }
}
