/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.scoping;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.resource.ResourceDescriptionProvider;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.domain.DomainMemberCollectionProvider;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.selectables.TrimPrefixSelectable;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.Scopes;
import org.eclipse.xtext.scoping.impl.AbstractGlobalScopeDelegatingScopeProvider;
import org.eclipse.xtext.scoping.impl.SelectableBasedScope;

import java.util.List;

public class OxstsLocalScopeProvider extends AbstractGlobalScopeDelegatingScopeProvider {

    @Inject
    private IQualifiedNameProvider qualifiedNameProvider;

    @Inject
    private DomainMemberCollectionProvider domainMemberCollectionProvider;

    @Inject
    private ResourceDescriptionProvider resourceDescriptionProvider;

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

        var resourceDescription = resourceDescriptionProvider.getResourceDescription(resource);
        var packageName = qualifiedNameProvider.getFullyQualifiedName(_package);
        var resourceSelectable = new TrimPrefixSelectable(resourceDescription, packageName);
        return SelectableBasedScope.createScope(globalScope, resourceSelectable, reference.getEReferenceType(), isIgnoreCase(reference));
    }

    protected IScope getInlinedOxstsScope(InlinedOxsts inlinedOxsts, EReference reference) {
        var resource = inlinedOxsts.eResource();
        var globalScope = getGlobalScope(resource, reference);

        if (reference == OxstsPackage.Literals.INLINED_OXSTS__CLASS_DECLARATION) {
            return globalScope;
        }

        var elements = getInlinedOxstsElements(inlinedOxsts);

        return Scopes.scopeFor(elements, QualifiedName.wrapper(NamingUtil::getName), globalScope);
    }

    protected Iterable<? extends EObject> getInlinedOxstsElements(InlinedOxsts inlinedOxsts) {
        if (inlinedOxsts.getRootFeature() != null) {
            return Iterables.concat(
                    inlinedOxsts.getVariables(),
                    List.of(inlinedOxsts.getRootFeature())
            );
        } else {
            return inlinedOxsts.getVariables();
        }
    }

    // caching feels unnecessary here, since most of the calculated instances are simple to create, or are cache anyway
    protected IScope getLocalScope(IScope containerScope, EObject context, EObject child, EReference reference) {
        if (
            reference == OxstsPackage.Literals.CLASS_DECLARATION__SUPER_TYPES
            || reference == OxstsPackage.Literals.FEATURE_DECLARATION__TYPE
        ) {
            return containerScope;
        }

        if (context instanceof SequenceOperation operation) {
            var index = operation.getSteps().indexOf(child);
            return Scopes.scopeFor(operation.eContents().subList(0, index), containerScope);
        }

        if (context instanceof DomainDeclaration declaration) {
            var memberCollection = domainMemberCollectionProvider.getMemberCollection(declaration).getMemberSelectable();
            return SelectableBasedScope.createScope(containerScope, memberCollection, reference.getEReferenceType(), isIgnoreCase(reference));
        }

        if (context instanceof Namespace namespace) {
            return Scopes.scopeFor(namespace.eContents(), QualifiedName.wrapper(NamingUtil::getName), containerScope);
        }

        return containerScope;
    }

}
