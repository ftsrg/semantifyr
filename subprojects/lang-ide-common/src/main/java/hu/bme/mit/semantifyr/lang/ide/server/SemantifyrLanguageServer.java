/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server;

import com.google.gson.JsonElement;
import com.google.inject.Inject;
import hu.bme.mit.semantifyr.lang.ide.server.concurrent.WorkManager;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.WorkDoneProgressCancelParams;
import org.eclipse.xtext.ide.server.LanguageServerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SemantifyrLanguageServer extends LanguageServerImpl {

    private static final Logger LOG = LoggerFactory.getLogger(SemantifyrLanguageServer.class);

    @Inject
    protected WorkManager workManager;

    @Inject
    protected ServerSettings serverSettings;

    @Override
    public void cancelProgress(WorkDoneProgressCancelParams params) {
        workManager.cancelProgress(params);
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        workManager.initialize(getLanguageClient());
        applyInitializationOptions(params);
        return super.initialize(params);
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        Object raw = params == null ? null : params.getSettings();
        if (raw instanceof JsonElement el) {
            LOG.info("workspace/didChangeConfiguration received");
            serverSettings.apply(el);
        } else if (raw != null) {
            LOG.warn(
                    "workspace/didChangeConfiguration payload is not JSON: {}",
                    raw.getClass().getName());
        }
        super.didChangeConfiguration(params);
    }

    private void applyInitializationOptions(InitializeParams params) {
        if (params == null) return;
        Object options = params.getInitializationOptions();
        if (options instanceof JsonElement el) {
            LOG.info("initialize with settings");
            serverSettings.apply(el);
        } else if (options != null) {
            LOG.warn("initialize options are not JSON: {}", options.getClass().getName());
        }
    }
}
