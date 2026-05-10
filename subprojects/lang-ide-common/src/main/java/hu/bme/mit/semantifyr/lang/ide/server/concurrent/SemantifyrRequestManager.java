/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package hu.bme.mit.semantifyr.lang.ide.server.concurrent;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.lang.ide.server.concurrent.jobs.AbstractJob;
import hu.bme.mit.semantifyr.lang.ide.server.concurrent.jobs.ReadJob;
import hu.bme.mit.semantifyr.lang.ide.server.concurrent.jobs.WriteJob;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import org.eclipse.xtext.ide.server.concurrent.AbstractRequestManager;
import org.eclipse.xtext.service.OperationCanceledManager;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.xbase.lib.Functions;

@Singleton
public class SemantifyrRequestManager extends AbstractRequestManager {

    protected final LockProvider lockProvider = new LockProvider();
    protected final ExecutorService executorService;
    protected WriteJob<?, ?> lastWriteJob;

    @Inject
    public SemantifyrRequestManager(
            ExecutorService executorService,
            OperationCanceledManager operationCanceledManager) {
        super(operationCanceledManager);
        this.executorService = executorService;
    }

    @Override
    public void shutdown() {
        executorService.shutdown();
    }

    @Override
    public <V> CompletableFuture<V> runRead(Functions.Function1<? super CancelIndicator, ? extends V> cancellable) {
        return submit(new ReadJob<>(lockProvider, cancellable));
    }

    @Override
    public synchronized <U, V> CompletableFuture<V> runWrite(
            Functions.Function0<? extends U> nonCancellable,
            Functions.Function2<? super CancelIndicator, ? super U, ? extends V> cancellable) {
        CompletableFuture<?> lastCancellation;

        if (lastWriteJob != null) {
            lastWriteJob.cancel();
            lastCancellation = lastWriteJob.getFuture();
        } else {
            lastCancellation = CompletableFuture.completedFuture(null);
        }

        var writeJob = new WriteJob<U, V>(lockProvider, nonCancellable, cancellable, lastCancellation);

        lastWriteJob = writeJob;

        return submit(writeJob);
    }

    public <T> T performBackgroundWork(Supplier<T> work) {
        lockProvider.releaseReadLock();
        try {
            return work.get();
        } finally {
            lockProvider.acquireReadLock();
        }
    }

    private <V> CompletableFuture<V> submit(AbstractJob<V> request) {
        executorService.submit(request);
        return request.getFuture();
    }
}
