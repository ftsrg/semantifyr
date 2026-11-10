/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.references;

import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.xtext.findReferences.IReferenceFinder;
import org.eclipse.xtext.findReferences.ReferenceAcceptor;
import org.eclipse.xtext.findReferences.TargetURICollector;
import org.eclipse.xtext.findReferences.TargetURIs;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.ide.server.DocumentExtensions;
import org.eclipse.xtext.ide.server.symbol.DocumentSymbolService;
import org.eclipse.xtext.ide.util.CancelIndicatorProgressMonitor;
import org.eclipse.xtext.resource.EObjectAtOffsetHelper;
import org.eclipse.xtext.resource.IResourceDescriptions;
import org.eclipse.xtext.resource.IResourceServiceProvider;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;

public class OxstsDocumentSymbolService extends DocumentSymbolService {

    @Inject
    private EObjectAtOffsetHelper eObjectAtOffsetHelper;

    @Inject
    private IReferenceFinder referenceFinder;

    @Inject
    private TargetURICollector targetURICollector;

    @Inject
    private Provider<TargetURIs> targetURIProvider;

    @Inject
    private IResourceServiceProvider.Registry resourceServiceProviderRegistry;

    @Inject
    private RedefinitionChainCollector redefinitionChainCollector;

    @Override
    public List<? extends Location> getReferences(
            Document document,
            XtextResource resource,
            ReferenceParams params,
            IReferenceFinder.IResourceAccess resourceAccess,
            IResourceDescriptions indexData,
            CancelIndicator cancelIndicator) {
        var offset = document.getOffSet(params.getPosition());
        var locations = new ArrayList<Location>();
        if (params.getContext().isIncludeDeclaration()) {
            var declaration = eObjectAtOffsetHelper.getElementWithNameAt(resource, offset);
            if (declaration != null) {
                for (var member : redefinitionChainCollector.collect(declaration)) {
                    var location = getSymbolLocation(member);
                    if (location != null) {
                        locations.add(location);
                    }
                }
            }
        }
        locations.addAll(getReferences(resource, offset, resourceAccess, indexData, cancelIndicator));
        return locations;
    }

    @Override
    public List<? extends Location> getReferences(
            XtextResource resource,
            int offset,
            IReferenceFinder.IResourceAccess resourceAccess,
            IResourceDescriptions indexData,
            CancelIndicator cancelIndicator) {
        var element = eObjectAtOffsetHelper.resolveElementAt(resource, offset);
        if (element == null) {
            return List.of();
        }
        var targetURIs = targetURIProvider.get();
        for (var member : redefinitionChainCollector.collect(element)) {
            targetURICollector.add(member, targetURIs);
        }
        var locations = new ArrayList<Location>();
        var acceptor = new ReferenceAcceptor(resourceServiceProviderRegistry, reference -> {
            var sourceUri = reference.getSourceEObjectUri();
            doRead(resourceAccess, sourceUri, source -> {
                var documentExtensions = resourceServiceProviderRegistry
                        .getResourceServiceProvider(sourceUri.trimFragment())
                        .get(DocumentExtensions.class);
                var location =
                        documentExtensions.newLocation(source, reference.getEReference(), reference.getIndexInList());
                if (location != null) {
                    locations.add(location);
                }
            });
        });
        referenceFinder.findAllReferences(
                targetURIs, resourceAccess, indexData, acceptor, new CancelIndicatorProgressMonitor(cancelIndicator));
        return locations;
    }
}
