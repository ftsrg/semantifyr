/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.references;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinersFinder;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RedefinableDeclaration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.xtext.resource.IResourceDescriptionsProvider;

public class RedefinitionChainCollector {

    @Inject
    private RedefinitionHandler redefinitionHandler;

    @Inject
    private RedefinersFinder redefinersFinder;

    @Inject
    private IResourceDescriptionsProvider resourceDescriptionsProvider;

    public Set<EObject> collect(EObject element) {
        if (!(element instanceof RedefinableDeclaration declaration)) {
            return Set.of(element);
        }

        // RedefinersFinder scans the resource set, so a redefiner in a file nobody opened is found only once loaded.
        loadIndexedResources(declaration.eResource().getResourceSet());

        var root = declaration;
        var visited = new HashSet<RedefinableDeclaration>();
        visited.add(root);
        var redefined = redefinitionHandler.getRedefinedDeclaration(root);
        while (redefined != null && visited.add(redefined)) {
            root = redefined;
            redefined = redefinitionHandler.getRedefinedDeclaration(root);
        }

        var chain = new LinkedHashSet<EObject>();
        var pending = new ArrayDeque<RedefinableDeclaration>();
        pending.add(root);
        while (!pending.isEmpty()) {
            var current = pending.poll();
            if (chain.add(current)) {
                pending.addAll(redefinersFinder.getRedefinerDeclarations(current));
            }
        }
        return chain;
    }

    private void loadIndexedResources(ResourceSet resourceSet) {
        var index = resourceDescriptionsProvider.getResourceDescriptions(resourceSet);
        var uris = new ArrayList<URI>();
        for (var description : index.getAllResourceDescriptions()) {
            uris.add(description.getURI());
        }
        for (var uri : uris) {
            resourceSet.getResource(uri, true);
        }
    }
}
