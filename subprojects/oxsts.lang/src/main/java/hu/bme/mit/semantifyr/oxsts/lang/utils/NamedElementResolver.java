/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.utils;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.resource.IResourceDescription;

public class NamedElementResolver {

    @Inject
    private IResourceDescription.Manager descriptionManager;

    public <T> T findQualifiedElement(Resource resource, Class<T> type, QualifiedName name) {
        var builtins = descriptionManager.getResourceDescription(resource);

        for (var candidate : builtins.getExportedObjects(OxstsPackage.Literals.ELEMENT, name, false)) {
            if (type.isInstance(candidate.getEObjectOrProxy())) {
                //noinspection unchecked
                return (T) candidate.getEObjectOrProxy();
            }
        }

        return null;
    }

}
