/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent.jobs;

import hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent.CompletableFutureCancelIndicator;

import java.util.concurrent.CompletableFuture;

public abstract class AbstractJob<V> implements Runnable {

    private final CompletableFuture<V> result;

    protected final CompletableFutureCancelIndicator cancelIndicator;

    public AbstractJob() {
        this.result = new CompletableFuture<>();
        this.cancelIndicator = new CompletableFutureCancelIndicator(result);
    }

    protected void complete(V value) {
        result.complete(value);
    }

    protected void cancel(boolean mayInterruptIfRunning) {
        result.cancel(mayInterruptIfRunning);
    }

    public final void cancel() {
        cancel(true);
    }

    public CompletableFuture<V> getFuture() {
        return this.result;
    }

}
