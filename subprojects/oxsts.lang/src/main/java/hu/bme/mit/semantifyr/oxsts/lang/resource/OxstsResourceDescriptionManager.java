/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.resource;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.resource.IDefaultResourceDescriptionStrategy;
import org.eclipse.xtext.resource.IResourceDescription;
import org.eclipse.xtext.resource.impl.DefaultResourceDescriptionManager;

public class OxstsResourceDescriptionManager extends DefaultResourceDescriptionManager {

    @Override
    protected IResourceDescription internalGetResourceDescription(
            Resource resource, IDefaultResourceDescriptionStrategy strategy) {
        return new OxstsResourceDescription(resource, strategy, getCache());
    }

    @Override
    protected boolean hasChanges(IResourceDescription.Delta delta, IResourceDescription candidate) {
        var superResult = super.hasChanges(delta, candidate);

        if (superResult) {
            return true;
        }

        var packageName = getPackageName(delta.getOld());
        if (packageName == null) {
            packageName = getPackageName(delta.getNew());
        }
        if (packageName == null) {
            // a file without a package exports nothing, only the files referencing it (inlined models) see a change
            return true;
        }

        if (packageName.equals(getPackageName(candidate))) {
            return true;
        }

        // Simplified heuristic: a delta is considered to have changes from the perspective of
        // candidate, if it has any reference to old resource.
        // If an element is referenced from candidate to old resource, then the importedName must
        // begin with the package name:
        //  1) it is either imported, in which case the import itself will match
        //  2) or it is referenced by FQN, in which case it must start with it
        for (var name : candidate.getImportedNames()) {
            if (name.startsWithIgnoreCase(packageName)) {
                return true;
            }
        }

        return false;
    }

    private QualifiedName getPackageName(IResourceDescription description) {
        if (description == null) {
            return null;
        }
        var packageDescriptions = description
                .getExportedObjectsByType(OxstsPackage.Literals.OXSTS_MODEL_PACKAGE)
                .iterator();
        if (!packageDescriptions.hasNext()) {
            return null;
        }
        return packageDescriptions.next().getQualifiedName();
    }
}
