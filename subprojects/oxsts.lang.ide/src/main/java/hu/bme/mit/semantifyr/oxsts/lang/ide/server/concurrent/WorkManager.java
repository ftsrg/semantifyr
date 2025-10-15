/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent;

import com.google.inject.Singleton;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class WorkManager {

    protected final ConcurrentHashMap<Either<String, Integer>, SimpleCancelIndicator> indicators = new ConcurrentHashMap<>();

    protected LanguageClient languageClient;

    public void checkCanceled(Either<String, Integer> token) {
        var indicator = indicators.get(token);
        if (indicator != null) {
            try {
                indicator.checkCanceled();
            } catch (CancellationException cancellationException) {
                indicators.remove(token);
                throw cancellationException;
            }
        }
    }

    public void cancelProgress(WorkDoneProgressCancelParams params) {
        var indicator = indicators.get(params.getToken());
        if (indicator != null) {
            indicator.cancel();
        }
    }

    public void initialize(LanguageClient languageClient) {
        this.languageClient = languageClient;
    }

    public void beginWork(Either<String, Integer> token, String title, String message) {
        indicators.put(token, new SimpleCancelIndicator());

        languageClient.createProgress(new WorkDoneProgressCreateParams(token)).join();

        var begin = new WorkDoneProgressBegin();
        begin.setTitle(title);
        begin.setMessage(message);
        begin.setPercentage(1);
        begin.setCancellable(true);

        languageClient.notifyProgress(new ProgressParams(token, Either.forLeft(begin)));
    }

    public void endWork(Either<String, Integer> token) {
        var end = new WorkDoneProgressEnd();
        languageClient.notifyProgress(new ProgressParams(token, Either.forLeft(end)));

        indicators.remove(token);
    }

    public void reportProgressOnWork(Either<String, Integer> token, String message, int percentage) {
        var progress = new WorkDoneProgressReport();
        progress.setCancellable(true);
        progress.setMessage(message);
        progress.setPercentage(percentage);
        languageClient.notifyProgress(new ProgressParams(token, Either.forLeft(progress)));
    }

}
