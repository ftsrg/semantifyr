/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.scoping;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.*;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.resource.ISelectable;
import org.eclipse.xtext.resource.impl.ResourceDescriptionsProvider;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.Scopes;
import org.eclipse.xtext.scoping.impl.AbstractGlobalScopeDelegatingScopeProvider;
import org.eclipse.xtext.scoping.impl.GlobalResourceDescriptionProvider;
import org.eclipse.xtext.scoping.impl.ScopeBasedSelectable;
import org.eclipse.xtext.scoping.impl.SelectableBasedScope;

public class GammaLocalScopeProvider extends AbstractGlobalScopeDelegatingScopeProvider {

    @Inject
    private GlobalResourceDescriptionProvider globalResourceDescriptionProvider;

    @Inject
    private ResourceDescriptionsProvider resourceDescriptionsProvider;

    @Inject
    private IQualifiedNameProvider qualifiedNameProvider;

    @Override
    public IScope getScope(EObject context, EReference reference) {
        if (context instanceof GammaRoot gammaRoot) {
            return getRootScope(gammaRoot, reference);
        }

        if (reference == GammaPackage.Literals.EXECUTION_TRACE__VERIFICATION_CASE
                || reference == GammaPackage.Literals.IMPORT_STATEMENT__IMPORTED_PACKAGE) {
            return getScope(context.eContainer(), reference);
        }

        var containerScope = getScope(context.eContainer(), reference);
        return getLocalScope(containerScope, context, reference);
    }

    protected IScope getRootScope(GammaRoot gammaRoot, EReference reference) {
        var resource = gammaRoot.eResource();
        var type = reference.getEReferenceType();
        var ignoreCase = isIgnoreCase(reference);
        // lookups through wrap record the names the builder relinks a file on when another file changes
        var index = new ScopeBasedSelectable(wrap(SelectableBasedScope.createScope(
                IScope.NULLSCOPE, resourceDescriptionsProvider.getResourceDescriptions(resource), type, ignoreCase)));

        if (reference == GammaPackage.Literals.IMPORT_STATEMENT__IMPORTED_PACKAGE) {
            return SelectableBasedScope.createScope(IScope.NULLSCOPE, index, type, ignoreCase);
        }

        var importsScope = getImportsScope(gammaRoot, index, type, ignoreCase);
        if (!(gammaRoot instanceof GammaModelPackage gammaModelPackage)) {
            return importsScope;
        }

        var packageName = qualifiedNameProvider.getFullyQualifiedName(gammaModelPackage);
        if (packageName == null) {
            return importsScope;
        }
        var packageScope = SelectableBasedScope.createScope(
                importsScope, new TrimPrefixSelectable(index, packageName), type, ignoreCase);
        var fileSelectable = getFileSelectable(resource, packageName);
        return SelectableBasedScope.createScope(packageScope, fileSelectable, type, ignoreCase);
    }

    protected IScope getImportsScope(GammaRoot gammaRoot, ISelectable index, EClass type, boolean ignoreCase) {
        var scope = IScope.NULLSCOPE;
        for (var importStatement : gammaRoot.getImports()) {
            var importedPackage = importStatement.getImportedPackage();
            if (importedPackage == null || importedPackage.eIsProxy()) {
                continue;
            }
            var importedName = qualifiedNameProvider.getFullyQualifiedName(importedPackage);
            if (importedName == null) {
                continue;
            }
            scope = SelectableBasedScope.createScope(
                    scope, new TrimPrefixSelectable(index, importedName), type, ignoreCase);
        }
        return scope;
    }

    protected ISelectable getFileSelectable(Resource resource, QualifiedName packageName) {
        var resourceDescription = globalResourceDescriptionProvider.getResourceDescription(resource);
        return new TrimPrefixSelectable(resourceDescription, packageName);
    }

    protected IScope getLocalScope(IScope containerScope, EObject context, EReference reference) {
        if (context instanceof State state) {
            return scopeFor(state.getRegions(), reference, containerScope);
        }

        if (context instanceof Region region) {
            return scopeFor(region.eContents(), reference, containerScope);
        }

        if (context instanceof ComponentDeclaration componentDeclaration) {
            return scopeFor(componentDeclaration.eContents(), reference, containerScope);
        }

        return containerScope;
    }

    // a port may share its interface's name, so a local element only shadows outer ones of its own type
    protected IScope scopeFor(Iterable<? extends EObject> elements, EReference reference, IScope parent) {
        var type = reference.getEReferenceType();
        return Scopes.scopeFor(Iterables.filter(elements, type::isInstance), parent);
    }
}
