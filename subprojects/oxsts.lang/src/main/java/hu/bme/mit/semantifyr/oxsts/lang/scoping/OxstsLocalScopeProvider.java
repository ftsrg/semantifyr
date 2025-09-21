/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.scoping;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.resource.ResourceDescriptionProvider;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.selectables.TrimPrefixSelectable;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.domain.DomainMemberCalculator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Package;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.scoping.IGlobalScopeProvider;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.Scopes;
import org.eclipse.xtext.scoping.impl.AbstractGlobalScopeDelegatingScopeProvider;
import org.eclipse.xtext.scoping.impl.SelectableBasedScope;

public class OxstsLocalScopeProvider extends AbstractGlobalScopeDelegatingScopeProvider {

    @Inject
    private IGlobalScopeProvider globalScopeProvider;

    @Inject
    private IQualifiedNameProvider qualifiedNameProvider;

    @Inject
    private DomainMemberCalculator domainMemberCalculator;

    @Inject
    private ResourceDescriptionProvider resourceDescriptionProvider;

    @Override
    public IScope getScope(EObject context, EReference reference) {
        if (context instanceof Package _package) {
            return getPackageScope(_package, reference);
        }

        var containerScope = getScope(context.eContainer(), reference);
        return getLocalScope(containerScope, context, reference);
    }

    protected IScope getPackageScope(Package _package, EReference reference) {
        var resource = _package.eResource();
        var globalScope = getGlobalScope(resource, reference);

        var resourceDescription = resourceDescriptionProvider.getResourceDescription(resource);
        var packageName = qualifiedNameProvider.getFullyQualifiedName(_package);
        var resourceSelectable = new TrimPrefixSelectable(resourceDescription, packageName);
        return SelectableBasedScope.createScope(globalScope, resourceSelectable, reference.getEReferenceType(), isIgnoreCase(reference));
    }

    // caching feels unnecessary here, since most of the calculated instances are simple to create, or are cache anyway
    protected IScope getLocalScope(IScope containerScope, EObject context, EReference reference) {
        if (
            reference == OxstsPackage.Literals.CLASS_DECLARATION__SUPER_TYPES
            || reference == OxstsPackage.Literals.FEATURE_DECLARATION__TYPE
        ) {
            return containerScope;
        }

        if (context instanceof DomainDeclaration declaration) {
            var memberCollection = domainMemberCalculator.getMemberCollection(declaration).getMembers();
            return SelectableBasedScope.createScope(containerScope, memberCollection, reference.getEReferenceType(), isIgnoreCase(reference));
        }

        if (context instanceof Namespace namespace) {
            return Scopes.scopeFor(namespace.eContents(), QualifiedName.wrapper(NamingUtil::getName), containerScope);
        }

        return containerScope;
    }

}
