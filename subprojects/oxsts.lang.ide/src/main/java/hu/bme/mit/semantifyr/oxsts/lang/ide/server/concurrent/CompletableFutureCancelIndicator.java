/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent;

import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.xtext.util.CancelIndicator;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

public class CompletableFutureCancelIndicator implements CancelIndicator, CancelChecker {

    private final CompletableFuture<?> result;

    public CompletableFutureCancelIndicator(CompletableFuture<?> result) {
        this.result = result;
    }

    @Override
    public boolean isCanceled() {
        return result.isCancelled();
    }

    @Override
    public void checkCanceled() {
        if (isCanceled()) {
            throw new CancellationException();
        }
    }

}
