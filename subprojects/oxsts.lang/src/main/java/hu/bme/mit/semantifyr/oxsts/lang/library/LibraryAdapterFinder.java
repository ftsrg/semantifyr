/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;

@Singleton
public class LibraryAdapterFinder {

    @Inject
    private Provider<LibraryAdapter> delegateProvider;

    public LibraryAdapter getOrInstall(EObject context) {
        var resource = context.eResource();
        if (resource == null) {
            throw new IllegalArgumentException("context is not in a resource");
        }
        return getOrInstall(resource);
    }

    public LibraryAdapter getOrInstall(Resource context) {
        var resourceSet = context.getResourceSet();
        if (resourceSet == null) {
            throw new IllegalArgumentException("context is not in a resource set");
        }
        return getOrInstall(resourceSet);
    }

    public LibraryAdapter getOrInstall(ResourceSet resourceSet) {
        var adapter = getAdapter(resourceSet);
        if (adapter == null) {
            adapter = delegateProvider.get();
            adapter.setResourceSet(resourceSet);
            resourceSet.eAdapters().add(adapter);
        }
        return adapter;
    }

    private static LibraryAdapter getAdapter(ResourceSet resourceSet) {
        return (LibraryAdapter) EcoreUtil.getAdapter(resourceSet.eAdapters(), LibraryAdapter.class);
    }

}
