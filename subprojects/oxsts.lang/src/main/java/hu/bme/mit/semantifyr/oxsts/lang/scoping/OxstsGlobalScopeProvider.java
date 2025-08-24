/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.scoping;

import com.google.common.base.Predicate;
import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.library.LibraryAdapterFinder;
import hu.bme.mit.semantifyr.oxsts.lang.resource.ResourceDescriptionProvider;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.imports.ImportCollector;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.selectables.CompositeSelectable;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.selectables.TrimPrefixSelectable;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.resource.ISelectable;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.impl.ResourceSetGlobalScopeProvider;
import org.eclipse.xtext.scoping.impl.SelectableBasedScope;
import org.eclipse.xtext.util.IResourceScopeCache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class OxstsGlobalScopeProvider extends ResourceSetGlobalScopeProvider {

    private static final String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.scoping.OxstsGlobalScopeProvider.CACHE_KEY";

    @Inject
    private LibraryAdapterFinder libraryAdapterFinder;

    @Inject
    private ImportCollector importCollector;

    @Inject
    private IResourceScopeCache cache;

    @Inject
    private ResourceDescriptionProvider resourceDescriptionProvider;

    @Override
    public IScope getScope(Resource resource, EReference reference, Predicate<IEObjectDescription> filter) {
        // TODO: should probably move to somewhere else, feels out of place here
        libraryAdapterFinder.getOrInstall(resource).loadLibraryResources();

        var globalScope = super.getScope(resource, reference, filter);

        if (reference == OxstsPackage.Literals.IMPORT_STATEMENT__IMPORTED_PACKAGE) {
            // do not try to collect imports while trying to link import statements!
            return globalScope;
        }

        var loadedImports = cache.get(CACHE_KEY, resource, () -> computeLoadedImports(resource));

        var implicitImportsScope = createScope(globalScope, loadedImports.implicitScopes(), reference, filter);
        var explicitImportsScope = createScope(implicitImportsScope, loadedImports.explicitScopes(), reference, filter);

        return explicitImportsScope;
    }

    protected LoadedImportScopes computeLoadedImports(Resource resource) {
        var imports = importCollector.getDirectImports(resource);
        var implicitScopes = new ArrayList<ISelectable>();
        var explicitScopes = new ArrayList<ISelectable>();
        for (var importEntry : imports) {
            var importedResourceUri = importEntry.uri();
            if (resource.getURI() == importedResourceUri) {
                continue;
            }
            var importedResourceDescription = resourceDescriptionProvider.getResourceDescription(resource.getResourceSet(), importedResourceUri);
            if (importedResourceDescription == null) {
                continue;
            }
            var packageName = importedResourceDescription.getExportedObjects().iterator().next().getQualifiedName();
            if (importEntry.isImplicit()) {
                implicitScopes.add(new TrimPrefixSelectable(importedResourceDescription, packageName));
            } else {
                explicitScopes.add(new TrimPrefixSelectable(importedResourceDescription, packageName));
            }
        }
        return new LoadedImportScopes(implicitScopes, explicitScopes);
    }

    protected IScope createScope(IScope parent, Collection<? extends ISelectable> children, EReference reference, Predicate<IEObjectDescription> filter) {
        var selectable = CompositeSelectable.of(children);
        return SelectableBasedScope.createScope(parent, selectable, filter, reference.getEReferenceType(), isIgnoreCase(reference));
    }

    protected record LoadedImportScopes(
            List<ISelectable> implicitScopes,
            List<ISelectable> explicitScopes
    ) { }

}
