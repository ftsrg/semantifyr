/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent;

import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.xtext.util.CancelIndicator;

import java.util.concurrent.CancellationException;

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
