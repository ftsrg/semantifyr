/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.scoping.imports;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.library.LibraryAdapter;
import hu.bme.mit.semantifyr.oxsts.lang.library.LibraryAdapterFinder;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.util.IResourceScopeCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Singleton
public class ImportCollector {
    private static final String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.scoping.imports.ImportCollector.IMPORTS";

    @Inject
    private IResourceScopeCache cache;

    @Inject
    private LibraryAdapterFinder libraryAdapterFinder;

    public List<Import> getDirectImports(Resource resource) {
        return cache.get(CACHE_KEY, resource, () -> this.computeDirectImports(resource));
    }

    protected List<Import> computeDirectImports(Resource resource) {
        if (resource.getContents().isEmpty()) {
            return Collections.emptyList();
        }
        var adapter = libraryAdapterFinder.getOrInstall(resource);

        var collection = new ArrayList<Import>();
        collectImplicitImports(collection, adapter);

        if (resource.getContents().getFirst() instanceof OxstsModelPackage _package) {
            collectExplicitImports(_package, collection);
        }

        return collection;
    }

    private void collectImplicitImports(List<Import> importCollection, LibraryAdapter adapter) {
        for (var implicitUri : adapter.getLibrary().getImplicitImports()) {
            importCollection.add(Import.implicit(implicitUri));
        }
    }

    private void collectExplicitImports(OxstsModelPackage _package, List<Import> collection) {
        for (var importStatement : _package.getImports()) {
            var importedPackage = importStatement.getImportedPackage();
            if (importedPackage != null && importedPackage.eResource() != null && importedPackage.eResource().getURI() != null) {
                collection.add(Import.explicit(importedPackage.eResource().getURI()));
            }
        }
    }

}
