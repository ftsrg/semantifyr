/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.scoping.imports;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.library.LibraryAdapter;
import hu.bme.mit.semantifyr.oxsts.lang.library.LibraryAdapterFinder;
import hu.bme.mit.semantifyr.oxsts.lang.resource.ResourceDescriptionProvider;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsPackage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.util.IResourceScopeCache;

public class ImportCollector {
    private static final String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.scoping.imports.ImportCollector.IMPORTS";

    @Inject
    private IResourceScopeCache cache;

    @Inject
    private LibraryAdapterFinder libraryAdapterFinder;

    @Inject
    private ResourceDescriptionProvider resourceDescriptionProvider;

    @Inject
    private IQualifiedNameProvider qualifiedNameProvider;

    public List<Import> getDirectImports(Resource resource) {
        return cache.get(CACHE_KEY, resource, () -> this.computeDirectImports(resource));
    }

    protected List<Import> computeDirectImports(Resource resource) {
        if (resource.getContents().isEmpty()) {
            return Collections.emptyList();
        }
        var adapter = libraryAdapterFinder.getOrInstall(resource);

        var collection = new ArrayList<Import>();
        collectImplicitImports(resource, collection, adapter);

        if (resource.getContents().getFirst() instanceof OxstsModelPackage _package) {
            collectExplicitImports(_package, collection);
        }

        return collection;
    }

    private void collectImplicitImports(Resource resource, List<Import> importCollection, LibraryAdapter adapter) {
        for (var implicitUri : adapter.getLibraryProvider().getImplicitImports()) {
            var description =
                    resourceDescriptionProvider.getResourceDescription(resource.getResourceSet(), implicitUri);
            if (description == null) {
                continue;
            }
            var packageDescriptions = description
                    .getExportedObjectsByType(OxstsPackage.Literals.OXSTS_MODEL_PACKAGE)
                    .iterator();
            if (packageDescriptions.hasNext()) {
                importCollection.add(Import.implicit(packageDescriptions.next().getQualifiedName()));
            }
        }
    }

    private void collectExplicitImports(OxstsModelPackage _package, List<Import> collection) {
        for (var importStatement : _package.getImports()) {
            var importedPackage = importStatement.getImportedPackage();
            if (importedPackage == null || importedPackage.eIsProxy()) {
                continue;
            }
            var packageName = qualifiedNameProvider.getFullyQualifiedName(importedPackage);
            if (packageName != null) {
                collection.add(Import.explicit(packageName));
            }
        }
    }
}
