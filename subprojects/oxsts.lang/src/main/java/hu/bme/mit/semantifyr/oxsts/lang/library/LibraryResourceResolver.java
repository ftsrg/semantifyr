/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;

@Singleton
public class LibraryResourceResolver {

    @Inject
    protected LibraryAdapterFinder libraryAdapterFinder;

    public Resource resolveResource(URI resourceUri, EObject context) {
        libraryAdapterFinder.getOrInstall(context);

        var resourceSet = context.eResource().getResourceSet();
        return resourceSet.getResource(resourceUri, false);
    }

}
