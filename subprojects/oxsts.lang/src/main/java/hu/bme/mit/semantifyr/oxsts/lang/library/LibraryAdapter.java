/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinLibrary;
import hu.bme.mit.semantifyr.oxsts.lang.library.internal.CompositeLibrary;
import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.xtext.EcoreUtil2;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LibraryAdapter extends AdapterImpl {
    private ResourceSet resourceSet;

    private Resource builtinResource;

    @Inject
    private BuiltinLibrary builtinLibrary;

    private final AdditionalLibrary additionalLibrary = new AdditionalLibrary();

    private OxstsLibrary oxstsLibrary;

    private boolean libraryResourcesLoaded = false;

    void setResourceSet(ResourceSet resourceSet) {
        if (this.resourceSet == resourceSet) {
            return;
        }

        this.resourceSet = resourceSet;
    }

    public void loadLibraryResources() {
        if (libraryResourcesLoaded) {
            return;
        }

        libraryResourcesLoaded = true;

        getLibrary().prepareLoading();

        var loadedResources = new ArrayList<Resource>();

        for (var uri : getLibrary().getIncludedResourceUris()) {
            var resource = resourceSet.getResource(uri, true);
            loadedResources.add(resource);
        }

        for (var resource : loadedResources) {
            EcoreUtil2.resolveAll(resource);
        }
    }

    @Override
    public boolean isAdapterForType(Object type) {
        return type == LibraryAdapter.class;
    }

    public void setAdditionalPaths(List<Path> paths) {
        additionalLibrary.setLibraryPaths(paths);
    }

    public OxstsLibrary getLibrary() {
        if (oxstsLibrary == null) {
            // FIXME: this is wrong right now. Should be replaced with injection of all libraries
            oxstsLibrary = new CompositeLibrary(List.of(builtinLibrary/*, additionalLibrary*/));
        }

        return oxstsLibrary;
    }

    public Resource getBuiltinResource() {
        if (builtinResource == null) {
            builtinResource = resourceSet.getResource(builtinLibrary.getBuiltinResourceUri(), true);
        }

        return builtinResource;
    }

}
