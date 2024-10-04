/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.xtext.ide.server.LanguageServerImpl;
import org.eclipse.xtext.ide.server.ServerModule;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.Channels;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Use this class to register ide components.
 */
public class OxstsIdeModule extends AbstractOxstsIdeModule {
    public static void main(final String[] args) throws IOException, InterruptedException, ExecutionException {
        Injector injector = Guice.createInjector(new ServerModule());
        LanguageServerImpl languageServer = injector.getInstance(LanguageServerImpl.class);
        Function<MessageConsumer, MessageConsumer> wrapper = consumer -> {
            MessageConsumer result = message -> {
                System.out.println(message);
                consumer.consume(message);
            };
            return result;
        };
        Launcher<LanguageClient> launcher = createSocketLauncher(languageServer, LanguageClient.class, new InetSocketAddress("localhost", 5007), Executors.newCachedThreadPool(), wrapper);
        languageServer.connect(launcher.getRemoteProxy());
        Future<?> future = launcher.startListening();
        while (!future.isDone()) {
            System.out.println("Waiting");
            Thread.sleep(10_000l);
        }
        System.out.println("Done");
        System.exit(0);
    }

    static <T> Launcher<T> createSocketLauncher(Object localService, Class<T> remoteInterface, SocketAddress socketAddress, ExecutorService executorService, Function<MessageConsumer, MessageConsumer> wrapper) throws IOException, ExecutionException, InterruptedException {
        AsynchronousServerSocketChannel serverSocket = AsynchronousServerSocketChannel.open().bind(socketAddress);
        AsynchronousSocketChannel socketChannel = serverSocket.accept().get();
        return Launcher.createIoLauncher(localService, remoteInterface, Channels.newInputStream(socketChannel), Channels.newOutputStream(socketChannel), executorService, wrapper);
    }
}
