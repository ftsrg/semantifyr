/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.concurrent;

import java.util.concurrent.CancellationException;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.xtext.util.CancelIndicator;

public class SimpleCancelIndicator implements CancelIndicator, CancelChecker {

    private boolean state = false;

    @Override
    public void checkCanceled() {
        if (isCanceled()) {
            throw new CancellationException();
        }
    }

    @Override
    public boolean isCanceled() {
        return state;
    }

    public void cancel() {
        state = true;
    }
}
