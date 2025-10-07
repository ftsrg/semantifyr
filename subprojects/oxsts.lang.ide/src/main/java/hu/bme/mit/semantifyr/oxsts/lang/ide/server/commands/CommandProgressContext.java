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

    private final CancelIndicator cancelIndicator;
    private final WorkManager workManager;
    private final Either<String, Integer> token = Either.forRight(random.nextInt());

    public CommandProgressContext(CancelIndicator cancelIndicator, WorkManager workManager) {
        this.cancelIndicator = cancelIndicator;
        this.workManager = workManager;
    }

    @Override
    public void checkIsCancelled() {
//        if (cancelIndicator.isCanceled()) {
//            end("The command has been cancelled!");
//            throw new CancellationException("This command has been cancelled!");
//        }

        workManager.checkCanceled(token);
    }

    public void begin(String title, String message) {
        workManager.beginWork(token, title, message);
    }

    public void end(String message) {
        workManager.endWork(token, message);
    }

    @Override
    public void reportProgress(String message, int percentage) {
        workManager.reportProgressOnWork(token, message, percentage);
    }

}
