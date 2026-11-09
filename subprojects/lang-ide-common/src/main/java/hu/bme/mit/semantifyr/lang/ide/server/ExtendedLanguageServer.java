/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server;

import com.google.gson.JsonElement;
import com.google.inject.Inject;
import hu.bme.mit.semantifyr.lang.ide.server.concurrent.WorkManager;
import hu.bme.mit.semantifyr.lang.ide.server.inlayhints.InlayHintService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.emf.common.util.URI;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkDoneProgressCancelParams;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.ide.server.LanguageServerImpl;
import org.eclipse.xtext.util.CancelIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtendedLanguageServer extends LanguageServerImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtendedLanguageServer.class);

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

    // Info is the lowest severity a validator can report, published as a hint so editors show no squiggle.
    @Override
    protected DiagnosticSeverity toDiagnosticSeverity(Severity severity) {
        if (severity == Severity.INFO) {
            return DiagnosticSeverity.Hint;
        }
        return super.toDiagnosticSeverity(severity);
    }

    @Override
    protected ServerCapabilities createServerCapabilities(InitializeParams params) {
        var capabilities = super.createServerCapabilities(params);
        var registry = getLanguagesRegistry();
        var providesInlayHints = registry.getExtensionToFactoryMap().keySet().stream()
                .map(extension -> registry.getResourceServiceProvider(URI.createURI("synth:///file." + extension)))
                .anyMatch(provider -> getService(provider, InlayHintService.class) != null);
        capabilities.setInlayHintProvider(providesInlayHints);
        return capabilities;
    }

    @Override
    public CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params) {
        return getRequestManager().runRead(cancelIndicator -> inlayHint(params, cancelIndicator));
    }

    protected List<InlayHint> inlayHint(InlayHintParams params, CancelIndicator cancelIndicator) {
        var uri = getURI(params.getTextDocument());
        var inlayHintService = getService(uri, InlayHintService.class);
        if (inlayHintService == null) {
            return List.of();
        }
        return getWorkspaceManager()
                .doRead(
                        uri,
                        (document, resource) ->
                                inlayHintService.computeInlayHints(document, resource, params, cancelIndicator));
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        Object raw = params == null ? null : params.getSettings();
        if (raw instanceof JsonElement el) {
            LOGGER.info("workspace/didChangeConfiguration received");
            serverSettings.apply(el);
        } else if (raw != null) {
            LOGGER.warn(
                    "workspace/didChangeConfiguration payload is not JSON: {}",
                    raw.getClass().getName());
        }
        super.didChangeConfiguration(params);
    }

    private void applyInitializationOptions(InitializeParams params) {
        if (params == null) return;
        Object options = params.getInitializationOptions();
        if (options instanceof JsonElement el) {
            LOGGER.info("initialize with settings");
            serverSettings.apply(el);
        } else if (options != null) {
            LOGGER.warn(
                    "initialize options are not JSON: {}", options.getClass().getName());
        }
    }
}
