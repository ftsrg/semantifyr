/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.scoping;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.domain.DomainMemberCollectionProvider;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.imports.ImportCollector;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.imports.PackageSelectableProvider;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.selectables.CompositeSelectable;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.selectables.TrimPrefixSelectable;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.resource.ISelectable;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.Scopes;
import org.eclipse.xtext.scoping.impl.AbstractGlobalScopeDelegatingScopeProvider;
import org.eclipse.xtext.scoping.impl.ScopeBasedSelectable;
import org.eclipse.xtext.scoping.impl.SelectableBasedScope;
import org.eclipse.xtext.util.Strings;

public class OxstsLocalScopeProvider extends AbstractGlobalScopeDelegatingScopeProvider {

    @Inject
    private IQualifiedNameProvider qualifiedNameProvider;

    @Inject
    private DomainMemberCollectionProvider domainMemberCollectionProvider;

    @Inject
    private ImportCollector importCollector;

    @Inject
    private PackageSelectableProvider packageSelectableProvider;

    @Override
    public IScope getScope(EObject context, EReference reference) {
        if (context instanceof OxstsModelPackage _package) {
            return getPackageScope(_package, reference);
        }

        if (context instanceof InlinedOxsts inlinedOxsts) {
            return getInlinedOxstsScope(inlinedOxsts, reference);
        }

        var parent = context.eContainer();

        var containerScope = getScope(parent, reference);
        return getLocalScope(containerScope, parent, context, reference);
    }

    protected IScope getPackageScope(OxstsModelPackage _package, EReference reference) {
        var resource = _package.eResource();
        var globalScope = getGlobalScope(resource, reference);

        if (reference == OxstsPackage.Literals.IMPORT_STATEMENT__IMPORTED_PACKAGE) {
            // do not try to collect imports while trying to link import statements!
            return globalScope;
        }

        var type = reference.getEReferenceType();
        var ignoreCase = isIgnoreCase(reference);
        // lookups through the wrapped global scope record the qualified names the builder matches changes against
        var index = new ScopeBasedSelectable(globalScope);
        var packageName = qualifiedNameProvider.getFullyQualifiedName(_package);
        var importsScope = getImportsScope(resource, globalScope, index, packageName, type, ignoreCase);
        var packageScope = importsScope;
        if (packageName != null) {
            packageScope = SelectableBasedScope.createScope(
                    importsScope, new TrimPrefixSelectable(index, packageName), type, ignoreCase);
        }
        var fileSelectable = packageSelectableProvider.getFileSelectable(_package);
        return SelectableBasedScope.createScope(packageScope, fileSelectable, type, ignoreCase);
    }

    protected IScope getImportsScope(
            Resource resource,
            IScope globalScope,
            ISelectable index,
            QualifiedName packageName,
            EClass type,
            boolean ignoreCase) {
        var implicitImports = new ArrayList<ISelectable>();
        var explicitImports = new ArrayList<ISelectable>();
        for (var importEntry : importCollector.getDirectImports(resource)) {
            if (importEntry.packageName().equals(packageName)) {
                continue;
            }
            var importSelectable = new TrimPrefixSelectable(index, importEntry.packageName());
            if (importEntry.isImplicit()) {
                implicitImports.add(importSelectable);
            } else {
                explicitImports.add(importSelectable);
            }
        }

        var implicitImportsScope = SelectableBasedScope.createScope(
                globalScope, CompositeSelectable.of(implicitImports), type, ignoreCase);
        return SelectableBasedScope.createScope(
                implicitImportsScope, CompositeSelectable.of(explicitImports), type, ignoreCase);
    }

    protected IScope getInlinedOxstsScope(InlinedOxsts inlinedOxsts, EReference reference) {
        var resource = inlinedOxsts.eResource();
        var type = reference.getEReferenceType();
        var ignoreCase = isIgnoreCase(reference);
        var globalScope = getGlobalScope(resource, reference);
        var importsScope =
                getImportsScope(resource, globalScope, new ScopeBasedSelectable(globalScope), null, type, ignoreCase);
        var unnamedPackagesScope = SelectableBasedScope.createScope(
                importsScope, getUnnamedPackagesSelectable(inlinedOxsts, reference), type, ignoreCase);

        if (reference == OxstsPackage.Literals.INLINED_OXSTS__CLASS_DECLARATION) {
            return unnamedPackagesScope;
        }

        var elements = getInlinedOxstsElements(inlinedOxsts);

        return Scopes.scopeFor(elements, QualifiedName.wrapper(NamingUtil::getName), unnamedPackagesScope);
    }

    // a case in an unnamed package is named by its simple name, and its model references the declarations of its file
    protected ISelectable getUnnamedPackagesSelectable(InlinedOxsts inlinedOxsts, EReference reference) {
        if (reference == OxstsPackage.Literals.INLINED_OXSTS__CLASS_DECLARATION) {
            var selectables = new ArrayList<ISelectable>();
            for (var resource :
                    List.copyOf(inlinedOxsts.eResource().getResourceSet().getResources())) {
                if (!resource.getContents().isEmpty()
                        && resource.getContents().getFirst() instanceof OxstsModelPackage _package
                        && Strings.isEmpty(_package.getName())) {
                    selectables.add(packageSelectableProvider.getUnnamedPackageSelectable(_package));
                }
            }
            return CompositeSelectable.of(selectables);
        }

        var classDeclaration = inlinedOxsts.getClassDeclaration();
        if (classDeclaration != null
                && !classDeclaration.eIsProxy()
                && EcoreUtil2.getRootContainer(classDeclaration) instanceof OxstsModelPackage _package
                && Strings.isEmpty(_package.getName())) {
            return packageSelectableProvider.getUnnamedPackageSelectable(_package);
        }

        return CompositeSelectable.of(List.of());
    }

    protected Iterable<? extends EObject> getInlinedOxstsElements(InlinedOxsts inlinedOxsts) {
        if (inlinedOxsts.getRootFeature() != null) {
            return Iterables.concat(inlinedOxsts.getVariables(), List.of(inlinedOxsts.getRootFeature()));
        } else {
            return inlinedOxsts.getVariables();
        }
    }

    // caching feels unnecessary here, since most of the calculated instances are simple to create,
    // or are cache anyway
    protected IScope getLocalScope(IScope containerScope, EObject context, EObject child, EReference reference) {
        if (context instanceof InlinedOxsts) {
            return containerScope;
        }

        if (context instanceof OxstsModelPackage) {
            return containerScope;
        }

        if (reference == OxstsPackage.Literals.CLASS_DECLARATION__SUPER_TYPES) {
            return containerScope;
        }

        if (context instanceof SequenceOperation operation) {
            var index = operation.getSteps().indexOf(child);
            return Scopes.scopeFor(operation.eContents().subList(0, index), containerScope);
        }

        if (context instanceof DomainDeclaration declaration) {
            var memberCollection = domainMemberCollectionProvider
                    .getMemberCollection(declaration)
                    .getMemberSelectable();
            return SelectableBasedScope.createScope(
                    containerScope, memberCollection, reference.getEReferenceType(), isIgnoreCase(reference));
        }

        if (context instanceof Namespace namespace) {
            return Scopes.scopeFor(namespace.eContents(), QualifiedName.wrapper(NamingUtil::getName), containerScope);
        }

        return containerScope;
    }
}
