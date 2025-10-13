/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.xtext.ide.server.concurrent.*;
import org.eclipse.xtext.service.OperationCanceledManager;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.xbase.lib.Functions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Singleton
public class PausableRequestManager extends AbstractRequestManager {

    private final ExecutorService readExecutorService;

    private final PausableThreadPoolExecutor writeExecutorService = PausableThreadPoolExecutor.newPausableSingleThreadExecutor(
            new ThreadFactoryBuilder().setDaemon(true).setNameFormat(getThreadQueueNameFormat()).build());

    private final List<AbstractRequest<?>> requests = new ArrayList<>();

    protected String getThreadQueueNameFormat() {
        return "RequestManager-Queue-%d";
    }

    @Inject
    public PausableRequestManager(ExecutorService executorService, OperationCanceledManager operationCanceledManager) {
        super(operationCanceledManager);
        this.readExecutorService = executorService;
    }

    @Override
    public void shutdown() {
        writeExecutorService.shutdown();
        readExecutorService.shutdown();
        cancel();
    }

    @Override
    public <V> CompletableFuture<V> runRead(Functions.Function1<? super CancelIndicator, ? extends V> cancellable) {
        return submit(new ReadRequest<>(this, cancellable, readExecutorService));
    }

    @Override
    public <U, V> CompletableFuture<V> runWrite(Functions.Function0<? extends U> nonCancellable, Functions.Function2<? super CancelIndicator, ? super U, ? extends V> cancellable) {
        return submit(new WriteRequest<>(this, nonCancellable, cancellable, CompletableFuture.completedFuture(null)));
    }

    public void pause() {
        writeExecutorService.pause();
    }

    public void resume() {
        writeExecutorService.resume();
    }

    protected <V> CompletableFuture<V> submit(AbstractRequest<V> request) {
        requests.add(request);
        writeExecutorService.submit(request);
        return request.get();
    }

    protected void cancel() {
        for (var request : requests) {
            request.cancel();
        }
    }

}
