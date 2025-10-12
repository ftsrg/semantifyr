/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent.WorkManager;
import hu.bme.mit.semantifyr.semantics.transformation.ProgressContext;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.Random;

public class CommandProgressContext implements ProgressContext {

    private static final Random random = new Random();

    private final WorkManager workManager;
    private final Either<String, Integer> token = Either.forRight(random.nextInt());

    public CommandProgressContext(WorkManager workManager) {
        this.workManager = workManager;
    }

    @Override
    public void checkIsCancelled() {
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
