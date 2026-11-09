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
import hu.bme.mit.semantifyr.lang.ide.server.hierarchy.TypeHierarchyItemData;
import hu.bme.mit.semantifyr.lang.ide.server.hierarchy.TypeHierarchyService;
import hu.bme.mit.semantifyr.lang.ide.server.inlayhints.InlayHintService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.ResourceSet;
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
import org.eclipse.xtext.ide.server.LanguageServerImpl;
import org.eclipse.xtext.resource.IResourceServiceProvider;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.xbase.lib.Functions.Function3;
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
        addInlayHintCapability(capabilities);
        addTypeHierarchyCapability(capabilities);
        addCallHierarchyCapability(capabilities);
        return capabilities;
    }

    private void addInlayHintCapability(ServerCapabilities capabilities) {
        var registry = getLanguagesRegistry();
        var providesInlayHints = registry.getExtensionToFactoryMap().keySet().stream()
                .map(extension -> registry.getResourceServiceProvider(URI.createURI("synth:///file." + extension)))
                .anyMatch(provider -> getService(provider, InlayHintService.class) != null);
        capabilities.setInlayHintProvider(providesInlayHints);
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

    private void addTypeHierarchyCapability(ServerCapabilities capabilities) {
        var registry = getLanguagesRegistry();
        var typeHierarchyProvider = registry.getExtensionToFactoryMap().keySet().stream()
                .map(extension -> registry.getResourceServiceProvider(URI.createURI("synth:///file." + extension)))
                .anyMatch(provider -> getService(provider, TypeHierarchyService.class) != null);
        capabilities.setTypeHierarchyProvider(typeHierarchyProvider);
    }

    @Override
    public CompletableFuture<List<TypeHierarchyItem>> prepareTypeHierarchy(TypeHierarchyPrepareParams params) {
        return getRequestManager().runRead(cancelIndicator -> {
            var uri = getURI(params);
            var service = getService(uri, TypeHierarchyService.class);
            if (service == null) {
                return List.of();
            }
            return getWorkspaceManager().doRead(uri, (document, resource) -> {
                if (resource == null) {
                    return List.of();
                }
                return service.prepare(resource, document, params);
            });
        });
    }

    @Override
    public CompletableFuture<List<TypeHierarchyItem>> typeHierarchySupertypes(TypeHierarchySupertypesParams params) {
        return relatedTypes(params.getItem(), TypeHierarchyService::supertypes);
    }

    @Override
    public CompletableFuture<List<TypeHierarchyItem>> typeHierarchySubtypes(TypeHierarchySubtypesParams params) {
        return relatedTypes(params.getItem(), TypeHierarchyService::subtypes);
    }

    private CompletableFuture<List<TypeHierarchyItem>> relatedTypes(
            TypeHierarchyItem item,
            Function3<TypeHierarchyService, ResourceSet, TypeHierarchyItem, List<TypeHierarchyItem>> query) {
        return getRequestManager().runRead(cancelIndicator -> {
            var uri = getURI(
                    new TextDocumentIdentifier(TypeHierarchyItemData.of(item).documentUri()));
            var service = getService(uri, TypeHierarchyService.class);
            if (service == null) {
                return List.of();
            }
            return getWorkspaceManager().doRead(uri, (document, resource) -> {
                if (resource == null) {
                    return List.of();
                }
                return query.apply(service, resource.getResourceSet(), item);
            });
        });
    }

    private void addCallHierarchyCapability(ServerCapabilities capabilities) {
        var supported = getLanguagesRegistry().getExtensionToFactoryMap().values().stream()
                .anyMatch(provider -> provider instanceof IResourceServiceProvider serviceProvider
                        && serviceProvider.get(CallHierarchyService.class) != null);
        capabilities.setCallHierarchyProvider(supported);
    }

    @Override
    public CompletableFuture<List<CallHierarchyItem>> prepareCallHierarchy(CallHierarchyPrepareParams params) {
        return getRequestManager().runRead(cancelIndicator -> {
            var uri = getURI(params.getTextDocument());
            var service = getService(uri, CallHierarchyService.class);
            if (service == null) {
                return List.of();
            }
            return getWorkspaceManager().doRead(uri, (document, resource) -> {
                if (resource == null) {
                    return List.<CallHierarchyItem>of();
                }
                return service.prepareCallHierarchy(document, resource, params, cancelIndicator);
            });
        });
    }

    @Override
    public CompletableFuture<List<CallHierarchyIncomingCall>> callHierarchyIncomingCalls(
            CallHierarchyIncomingCallsParams params) {
        return getRequestManager().runRead(cancelIndicator -> {
            var uri = getURI(new TextDocumentIdentifier(params.getItem().getUri()));
            var service = getService(uri, CallHierarchyService.class);
            if (service == null) {
                return List.of();
            }
            return getWorkspaceManager().doRead(uri, (document, resource) -> {
                if (resource == null) {
                    return List.<CallHierarchyIncomingCall>of();
                }
                return service.incomingCalls(
                        resource,
                        params.getItem(),
                        getWorkspaceResourceAccess(),
                        getWorkspaceManager().getIndex(),
                        cancelIndicator);
            });
        });
    }

    @Override
    public CompletableFuture<List<CallHierarchyOutgoingCall>> callHierarchyOutgoingCalls(
            CallHierarchyOutgoingCallsParams params) {
        return getRequestManager().runRead(cancelIndicator -> {
            var uri = getURI(new TextDocumentIdentifier(params.getItem().getUri()));
            var service = getService(uri, CallHierarchyService.class);
            if (service == null) {
                return List.of();
            }
            return getWorkspaceManager().doRead(uri, (document, resource) -> {
                if (resource == null) {
                    return List.<CallHierarchyOutgoingCall>of();
                }
                return service.outgoingCalls(resource, params.getItem(), cancelIndicator);
            });
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
