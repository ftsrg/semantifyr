/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.xtext.ide.server.concurrent.*;
import org.eclipse.xtext.service.OperationCanceledManager;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.xbase.lib.Functions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Singleton
public class PausableRequestManager extends RequestManager {

    private final List<AbstractRequest<?>> requests = new ArrayList<>();

    private final Object lock = new Object();
    private boolean pausedFlag = false;
    private final Queue<DeferredRequest<?>> deferredRequests = new ArrayDeque<>();

    @Override
    public void shutdown() {
        synchronized (lock) {
            pausedFlag = false;

            while (deferredRequests.peek() != null) {
                var next = deferredRequests.poll();

                submit(next.getDeferred());
            }

            super.shutdown();
        }
    }

    @Inject
    public PausableRequestManager(ExecutorService executorService, OperationCanceledManager operationCanceledManager) {
        super(executorService, operationCanceledManager);
    }

    @Override
    public synchronized <V> CompletableFuture<V> runRead(Functions.Function1<? super CancelIndicator, ? extends V> cancellable) {
        synchronized (lock) {
            Functions.Function0<AbstractRequest<V>> creator = () -> new ReadRequest<>(this, cancellable, getParallelExecutorService());

            if (pausedFlag) {
                var deferred = new DeferredRequest<V>(this, creator);
                deferredRequests.add(deferred);
                return deferred.get();
            }

            return submit(creator.apply());
        }
    }

    @Override
    public synchronized <U, V> CompletableFuture<V> runWrite(Functions.Function0<? extends U> nonCancellable, Functions.Function2<? super CancelIndicator, ? super U, ? extends V> cancellable) {
        synchronized (lock) {
            Functions.Function0<AbstractRequest<V>> creator = () -> new WriteRequest<>(this, nonCancellable, cancellable, CompletableFuture.completedFuture(null));

            if (pausedFlag) {
                var deferred = new DeferredRequest<V>(this, creator);
                deferredRequests.add(deferred);
                return deferred.get();
            }

            return submit(creator.apply());
        }
    }

//    @Override
//    protected void addRequest(AbstractRequest<?> request) {
//        super.addRequest(request);
//        requests.add(request);
////        request.get().handle((v, t) -> {
////            removeRequest(request);
////            return null;
////        });
//    }

    protected CompletableFuture<Void> waitForRequests() {
        synchronized (lock) {
            var futures = new CompletableFuture<?>[requests.size()];
            for (int i = 0; i < futures.length; i++) {
                futures[i] = requests.get(i).get();
            }
            return CompletableFuture.allOf(futures);
        }
    }

    protected void removeRequest(AbstractRequest<?> request) {
        synchronized (lock) {
            requests.remove(request);
        }
    }

    public void pause() {
        synchronized (lock) {
            pausedFlag = true;
        }
    }

    public void resume() {
        synchronized (lock) {
            pausedFlag = false;

            while (deferredRequests.peek() != null) {
                var next = deferredRequests.poll();

                submit(next.getDeferred());
            }
        }
    }

}
