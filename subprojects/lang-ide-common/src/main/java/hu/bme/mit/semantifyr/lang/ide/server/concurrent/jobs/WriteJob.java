/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.concurrent.jobs;

import hu.bme.mit.semantifyr.lang.ide.server.concurrent.SemantifyrRequestManager;
import java.util.concurrent.CompletableFuture;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.xbase.lib.Functions;

public class WriteJob<U, V> extends AbstractJob<V> {

    private final SemantifyrRequestManager semantifyrRequestManager;
    private final Functions.Function0<? extends U> nonCancellable;
    private final Functions.Function2<? super CancelIndicator, ? super U, ? extends V> cancellable;
    private final CompletableFuture<?> lastCancellation;

    public WriteJob(
            SemantifyrRequestManager semantifyrRequestManager,
            Functions.Function0<? extends U> nonCancellable,
            Functions.Function2<? super CancelIndicator, ? super U, ? extends V> cancellable,
            CompletableFuture<?> lastCancellation) {
        super();
        this.semantifyrRequestManager = semantifyrRequestManager;
        this.nonCancellable = nonCancellable;
        this.cancellable = cancellable;
        this.lastCancellation = lastCancellation;
    }

    @Override
    public void run() {
        try {
            lastCancellation.join();
        } catch (Throwable ignored) {
        }

        semantifyrRequestManager.acquireWriteLock();

        try {
            doRun();
        } catch (Throwable throwable) {
            getFuture().completeExceptionally(throwable);
        } finally {
            semantifyrRequestManager.releaseWriteLock();
        }
    }

    protected void doRun() {
        var intermediateResult = nonCancellable.apply();
        cancelIndicator.checkCanceled();
        var writeResult = cancellable.apply(cancelIndicator, intermediateResult);
        complete(writeResult);
    }
}
