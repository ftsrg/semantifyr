/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent.jobs.AbstractJob;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent.jobs.ReadJob;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent.jobs.WriteJob;
import org.eclipse.xtext.ide.server.concurrent.*;
import org.eclipse.xtext.service.OperationCanceledManager;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.xbase.lib.Functions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Singleton
public class SemantifyrRequestManager extends AbstractRequestManager {

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);
    private final Lock readLock = readWriteLock.readLock();
    private final Lock writeLock = readWriteLock.writeLock();
    private final ExecutorService executorService;
    private final List<AbstractJob<?>> requests = new ArrayList<>();
    private List<AbstractJob<?>> interruptableRequests = new ArrayList<>();

    @Inject
    public SemantifyrRequestManager(ExecutorService executorService, OperationCanceledManager operationCanceledManager) {
        super(operationCanceledManager);
        this.executorService = executorService;
    }

    public void acquireWriteLock() {
        writeLock.lock();
    }

    public void acquireReadLock() {
        readLock.lock();
    }

    public void releaseWriteLock() {
        writeLock.unlock();
    }

    public void releaseReadLock() {
        readLock.unlock();
    }

    @Override
    public void shutdown() {
        executorService.shutdown();

        cancel();
    }

    @Override
    public <V> CompletableFuture<V> runRead(Functions.Function1<? super CancelIndicator, ? extends V> cancellable) {
        return submit(new ReadJob<>(this, cancellable));
    }

    @Override
    public <U, V> CompletableFuture<V> runWrite(Functions.Function0<? extends U> nonCancellable, Functions.Function2<? super CancelIndicator, ? super U, ? extends V> cancellable) {
        return submit(new WriteJob<U, V>(this, nonCancellable, cancellable, CompletableFuture.completedFuture(null)));
    }

    public <U, V> CompletableFuture<V> runInterruptableWrite(Functions.Function0<? extends U> nonCancellable, Functions.Function2<? super CancelIndicator, ? super U, ? extends V> cancellable) {
        var lastCancellation = cancelInterruptibleRequests();

        var writeRequest = new WriteJob<U, V>(this, nonCancellable, cancellable, lastCancellation);

        interruptableRequests.add(writeRequest);

        return submit(writeRequest);
    }

    private <V> CompletableFuture<V> submit(AbstractJob<V> request) {
        requests.add(request);
        executorService.submit(request);
        return request.getFuture();
    }

    private void cancel() {
        for (var request : requests) {
            request.cancel();
        }
    }

    private CompletableFuture<?> cancelInterruptibleRequests() {
        List<AbstractJob<?>> localRequests = interruptableRequests;
        interruptableRequests = new ArrayList<>();
        CompletableFuture<?>[] cfs = new CompletableFuture<?>[localRequests.size()];
        for (int i = 0; i < localRequests.size(); i++) {
            var request = localRequests.get(i);
            request.cancel();
            cfs[i] = request.getFuture();
        }
        return CompletableFuture.allOf(cfs);
    }

}
