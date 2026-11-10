/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server;

import com.google.gson.JsonElement;
import com.google.inject.Inject;
import hu.bme.mit.semantifyr.lang.ide.server.callhierarchy.CallHierarchyService;
import hu.bme.mit.semantifyr.lang.ide.server.concurrent.WorkManager;
import hu.bme.mit.semantifyr.lang.ide.server.hierarchy.HierarchyItemData;
import hu.bme.mit.semantifyr.lang.ide.server.hierarchy.TypeHierarchyService;
import hu.bme.mit.semantifyr.lang.ide.server.inlayhints.InlayHintService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.emf.common.util.URI;
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.lsp4j.TypeHierarchySubtypesParams;
import org.eclipse.lsp4j.TypeHierarchySupertypesParams;
import org.eclipse.lsp4j.WorkDoneProgressCancelParams;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.ide.server.LanguageServerImpl;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.xbase.lib.Functions.Function3;
import org.eclipse.xtext.xbase.lib.Functions.Function4;
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
        capabilities.setInlayHintProvider(isBoundByAnyLanguage(InlayHintService.class));
        capabilities.setTypeHierarchyProvider(isBoundByAnyLanguage(TypeHierarchyService.class));
        capabilities.setCallHierarchyProvider(isBoundByAnyLanguage(CallHierarchyService.class));
        return capabilities;
    }

    private boolean isBoundByAnyLanguage(Class<?> serviceType) {
        var registry = getLanguagesRegistry();
        return registry.getExtensionToFactoryMap().keySet().stream()
                .map(extension -> registry.getResourceServiceProvider(URI.createURI("synth:///file." + extension)))
                .anyMatch(provider -> getService(provider, serviceType) != null);
    }

    @Override
    public CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params) {
        return readDocument(
                params.getTextDocument(),
                InlayHintService.class,
                (service, document, resource, cancelIndicator) ->
                        service.computeInlayHints(document, resource, params, cancelIndicator));
    }

    @Override
    public CompletableFuture<List<TypeHierarchyItem>> prepareTypeHierarchy(TypeHierarchyPrepareParams params) {
        return readDocument(
                params.getTextDocument(),
                TypeHierarchyService.class,
                (service, document, resource, cancelIndicator) ->
                        service.prepareTypeHierarchy(document, resource, params, cancelIndicator));
    }

    @Override
    public CompletableFuture<List<TypeHierarchyItem>> typeHierarchySupertypes(TypeHierarchySupertypesParams params) {
        return readItem(
                params.getItem().getData(),
                TypeHierarchyService.class,
                (service, resource, cancelIndicator) -> service.supertypes(resource, params.getItem()));
    }

    @Override
    public CompletableFuture<List<TypeHierarchyItem>> typeHierarchySubtypes(TypeHierarchySubtypesParams params) {
        return readItem(
                params.getItem().getData(),
                TypeHierarchyService.class,
                (service, resource, cancelIndicator) -> service.subtypes(resource, params.getItem()));
    }

    @Override
    public CompletableFuture<List<CallHierarchyItem>> prepareCallHierarchy(CallHierarchyPrepareParams params) {
        return readDocument(
                params.getTextDocument(),
                CallHierarchyService.class,
                (service, document, resource, cancelIndicator) ->
                        service.prepareCallHierarchy(document, resource, params, cancelIndicator));
    }

    @Override
    public CompletableFuture<List<CallHierarchyIncomingCall>> callHierarchyIncomingCalls(
            CallHierarchyIncomingCallsParams params) {
        return readItem(
                params.getItem().getData(),
                CallHierarchyService.class,
                (service, resource, cancelIndicator) -> service.incomingCalls(
                        resource,
                        params.getItem(),
                        getWorkspaceResourceAccess(),
                        getWorkspaceManager().getIndex(),
                        cancelIndicator));
    }

    @Override
    public CompletableFuture<List<CallHierarchyOutgoingCall>> callHierarchyOutgoingCalls(
            CallHierarchyOutgoingCallsParams params) {
        return readItem(
                params.getItem().getData(),
                CallHierarchyService.class,
                (service, resource, cancelIndicator) -> service.outgoingCalls(resource, params.getItem()));
    }

    protected <S, T> CompletableFuture<List<T>> readDocument(
            TextDocumentIdentifier textDocument,
            Class<S> serviceType,
            Function4<S, Document, XtextResource, CancelIndicator, List<T>> request) {
        return getRequestManager().runRead(cancelIndicator -> {
            return read(getURI(textDocument), serviceType, request, cancelIndicator);
        });
    }

    protected <S, T> CompletableFuture<List<T>> readItem(
            Object itemData, Class<S> serviceType, Function3<S, XtextResource, CancelIndicator, List<T>> request) {
        return getRequestManager().runRead(cancelIndicator -> {
            var textDocument =
                    new TextDocumentIdentifier(HierarchyItemData.of(itemData).documentUri());
            return read(
                    getURI(textDocument),
                    serviceType,
                    (service, document, resource, readCancelIndicator) ->
                            request.apply(service, resource, readCancelIndicator),
                    cancelIndicator);
        });
    }

    private <S, T> List<T> read(
            URI uri,
            Class<S> serviceType,
            Function4<S, Document, XtextResource, CancelIndicator, List<T>> request,
            CancelIndicator cancelIndicator) {
        var service = getService(uri, serviceType);
        if (service == null) {
            return List.of();
        }
        return getWorkspaceManager().doRead(uri, (document, resource) -> {
            if (resource == null) {
                return List.<T>of();
            }
            return request.apply(service, document, resource, cancelIndicator);
        });
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
