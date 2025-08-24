/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.resource;

import com.google.inject.Inject;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.xtext.resource.IResourceDescription;
import org.eclipse.xtext.scoping.impl.GlobalResourceDescriptionProvider;

public class ResourceDescriptionProvider {

    @Inject
    private GlobalResourceDescriptionProvider globalResourceDescriptionProvider;

    public IResourceDescription getResourceDescription(Resource resource) {
        return globalResourceDescriptionProvider.getResourceDescription(resource);
    }

    public IResourceDescription getResourceDescription(ResourceSet resourceSet, URI uri) {
        var loadedResource = resourceSet.getResource(uri, false);
        if (loadedResource == null) {
            return null;
        }
        return globalResourceDescriptionProvider.getResourceDescription(loadedResource);
    }

}
