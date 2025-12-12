/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent.jobs;

import hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent.SemantifyrRequestManager;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.xbase.lib.Functions;

public class ReadJob<V> extends AbstractJob<V> {

    private final SemantifyrRequestManager semantifyrRequestManager;
    private final Functions.Function1<? super CancelIndicator, ? extends V> cancellable;

    public ReadJob(SemantifyrRequestManager semantifyrRequestManager, Functions.Function1<? super CancelIndicator, ? extends V> cancellable) {
        super();
        this.semantifyrRequestManager = semantifyrRequestManager;
        this.cancellable = cancellable;
    }

    @Override
    public void run() {
        semantifyrRequestManager.acquireReadLock();

        try {
            doRun();
        } catch (Throwable throwable) {
            getFuture().completeExceptionally(throwable);
        } finally {
            semantifyrRequestManager.releaseReadLock();
        }
    }

    protected void doRun() {
        var readResult = cancellable.apply(cancelIndicator);
        complete(readResult);
    }

}
