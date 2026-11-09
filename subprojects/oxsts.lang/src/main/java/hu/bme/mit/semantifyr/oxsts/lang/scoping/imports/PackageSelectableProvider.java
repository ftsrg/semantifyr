/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.scoping.imports;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.resource.ResourceDescriptionProvider;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.selectables.TrimPrefixSelectable;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import java.util.ArrayList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.resource.ISelectable;
import org.eclipse.xtext.scoping.Scopes;
import org.eclipse.xtext.scoping.impl.MultimapBasedSelectable;

public class PackageSelectableProvider {

    @Inject
    private IQualifiedNameProvider qualifiedNameProvider;

    @Inject
    private ResourceDescriptionProvider resourceDescriptionProvider;

    public ISelectable getFileSelectable(OxstsModelPackage modelPackage) {
        var packageName = qualifiedNameProvider.getFullyQualifiedName(modelPackage);
        if (packageName == null) {
            return getUnnamedPackageSelectable(modelPackage);
        }
        var resourceDescription = resourceDescriptionProvider.getResourceDescription(modelPackage.eResource());
        return new TrimPrefixSelectable(resourceDescription, packageName);
    }

    // an unnamed package exports nothing, so its declarations are read from the file itself
    public ISelectable getUnnamedPackageSelectable(OxstsModelPackage modelPackage) {
        var elements = new ArrayList<EObject>();
        for (var declaration : modelPackage.getDeclarations()) {
            elements.add(declaration);
            if (declaration instanceof EnumDeclaration enumDeclaration) {
                elements.addAll(enumDeclaration.getLiterals());
            }
        }
        return new MultimapBasedSelectable(Scopes.scopedElementsFor(elements, qualifiedNameProvider));
    }
}
