/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library;

import com.google.inject.Inject;
import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.emf.ecore.resource.ResourceSet;

public class LibraryAdapter extends AdapterImpl {
    private ResourceSet resourceSet;

    @Inject
    private OxstsLibraryProvider oxstsLibraryProvider;

    private OxstsLibrary oxstsLibrary;

    private boolean libraryResourcesLoaded = false;

    void setResourceSet(ResourceSet resourceSet) {
        if (this.resourceSet == resourceSet) {
            return;
        }

        this.resourceSet = resourceSet;
        loadLibraryResources();
    }

    private void loadLibraryResources() {
        if (libraryResourcesLoaded) {
            return;
        }

        libraryResourcesLoaded = true;

        getLibraryProvider().loadLibrary(resourceSet);
    }

    @Override
    public boolean isAdapterForType(Object type) {
        return type == LibraryAdapter.class;
    }

    public OxstsLibrary getLibraryProvider() {
        if (oxstsLibrary == null) {
            oxstsLibrary = oxstsLibraryProvider.getLibrary();
        }

        return oxstsLibrary;
    }

}
