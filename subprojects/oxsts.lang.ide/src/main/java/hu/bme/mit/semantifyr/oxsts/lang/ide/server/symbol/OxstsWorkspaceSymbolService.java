/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.symbol;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.library.LibraryAdapterFinder;
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameConverter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.xtext.findReferences.IReferenceFinder.IResourceAccess;
import org.eclipse.xtext.ide.editor.contentassist.IPrefixMatcher;
import org.eclipse.xtext.ide.server.UriExtensions;
import org.eclipse.xtext.ide.server.symbol.HierarchicalDocumentSymbolService;
import org.eclipse.xtext.ide.server.symbol.WorkspaceSymbolService;
import org.eclipse.xtext.resource.IResourceDescriptions;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.service.OperationCanceledManager;
import org.eclipse.xtext.util.CancelIndicator;

@Singleton
public class OxstsWorkspaceSymbolService extends WorkspaceSymbolService {

    @Inject
    private HierarchicalDocumentSymbolService documentSymbolService;

    @Inject
    private LibraryAdapterFinder libraryAdapterFinder;

    @Inject
    private IPrefixMatcher prefixMatcher;

    @Inject
    private OxstsQualifiedNameConverter qualifiedNameConverter;

    @Inject
    private UriExtensions uriExtensions;

    @Inject
    private OperationCanceledManager operationCanceledManager;

    @Override
    public List<? extends WorkspaceSymbol> getSymbols(
            String query,
            IResourceAccess resourceAccess,
            IResourceDescriptions indexData,
            CancelIndicator cancelIndicator) {
        var symbols = new ArrayList<WorkspaceSymbol>();
        var visitedUris = new HashSet<URI>();
        for (var resourceDescription : indexData.getAllResourceDescriptions()) {
            operationCanceledManager.checkCanceled(cancelIndicator);
            var resourceUri = resourceDescription.getURI();
            if (visitedUris.contains(resourceUri)) {
                continue;
            }
            resourceAccess.readOnly(resourceUri, resourceSet -> {
                resourceSet.getResource(resourceUri, true);
                collectResourceSetSymbols(resourceSet, query, visitedUris, symbols, cancelIndicator);
                return null;
            });
        }
        return symbols;
    }

    // The libraries live in the resource set, outside the index.
    private void collectResourceSetSymbols(
            ResourceSet resourceSet,
            String query,
            Set<URI> visitedUris,
            List<WorkspaceSymbol> symbols,
            CancelIndicator cancelIndicator) {
        libraryAdapterFinder.getOrInstall(resourceSet);
        for (var resource : List.copyOf(resourceSet.getResources())) {
            operationCanceledManager.checkCanceled(cancelIndicator);
            if (resource instanceof XtextResource xtextResource && visitedUris.add(resource.getURI())) {
                var uri = uriExtensions.toUriString(resource.getURI());
                for (var symbol : documentSymbolService.getSymbols(xtextResource, cancelIndicator)) {
                    collectSymbols(symbol, null, uri, query, symbols);
                }
            }
        }
    }

    private void collectSymbols(
            DocumentSymbol symbol, String containerName, String uri, String query, List<WorkspaceSymbol> symbols) {
        if (prefixMatcher.isCandidateMatchingPrefix(symbol.getName(), query)) {
            var location = new Location(uri, symbol.getSelectionRange());
            symbols.add(
                    new WorkspaceSymbol(symbol.getName(), symbol.getKind(), Either.forLeft(location), containerName));
        }
        var qualifiedName = qualify(containerName, symbol.getName());
        for (var child : symbol.getChildren()) {
            collectSymbols(child, qualifiedName, uri, query, symbols);
        }
    }

    private String qualify(String containerName, String name) {
        if (containerName == null) {
            return name;
        }
        return containerName + qualifiedNameConverter.getDelimiter() + name;
    }
}
