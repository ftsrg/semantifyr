/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.concurrent.jobs;

import hu.bme.mit.semantifyr.lang.ide.server.concurrent.LockProvider;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.xbase.lib.Functions;

public class ReadJob<V> extends AbstractJob<V> {

    private final LockProvider lockProvider;
    private final Functions.Function1<? super CancelIndicator, ? extends V> cancellable;

    public ReadJob(LockProvider lockProvider, Functions.Function1<? super CancelIndicator, ? extends V> cancellable) {
        super();
        this.lockProvider = lockProvider;
        this.cancellable = cancellable;
    }

    @Override
    public void run() {
        lockProvider.acquireReadLock();

        try {
            doRun();
        } catch (Throwable throwable) {
            getFuture().completeExceptionally(throwable);
        } finally {
            lockProvider.releaseReadLock();
        }
    }

    protected void doRun() {
        var readResult = cancellable.apply(cancelIndicator);
        complete(readResult);
    }
}
