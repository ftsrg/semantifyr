/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent.WorkManager;
import hu.bme.mit.semantifyr.semantics.transformation.ProgressContext;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.xtext.util.CancelIndicator;

import java.util.Random;
import java.util.concurrent.CancellationException;

public class CommandProgressContext implements ProgressContext {

    private static final Random random = new Random();

    private final WorkManager workManager;
    private final CancelIndicator cancelIndicator;
    private final Either<String, Integer> token = Either.forRight(random.nextInt());

    public CommandProgressContext(WorkManager workManager, CancelIndicator cancelIndicator) {
        this.workManager = workManager;
        this.cancelIndicator = cancelIndicator;
    }

    @Override
    public void checkIsCancelled() {
        if (cancelIndicator.isCanceled()) {
            throw new CancellationException();
        }
        workManager.checkCanceled(token);
    }

    public void begin(String title, String message) {
        workManager.beginWork(token, title, message);
    }

    public void end() {
        workManager.endWork(token);
    }

    @Override
    public void reportProgress(String message, int percentage) {
        workManager.reportProgressOnWork(token, message, percentage);
    }

    @Override
    public void reportProgress(String message) {
        workManager.reportProgressOnWork(token, message);
    }

}
