/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.scoping;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.*;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.Scopes;
import org.eclipse.xtext.scoping.impl.AbstractGlobalScopeDelegatingScopeProvider;
import org.eclipse.xtext.scoping.impl.GlobalResourceDescriptionProvider;
import org.eclipse.xtext.scoping.impl.SelectableBasedScope;

public class GammaLocalScopeProvider extends AbstractGlobalScopeDelegatingScopeProvider {

    @Inject
    private GlobalResourceDescriptionProvider globalResourceDescriptionProvider;

    @Inject
    private IQualifiedNameProvider qualifiedNameProvider;

    @Override
    public IScope getScope(EObject context, EReference reference) {
        if (context instanceof GammaModelPackage gammaModelPackage) {
            return getGammaModelScope(gammaModelPackage, reference);
        }

        var containerScope = getScope(context.eContainer(), reference);
        return getLocalScope(containerScope, context);
    }

    protected IScope getGammaModelScope(GammaModelPackage gammaModelPackage, EReference reference) {
        var resource = gammaModelPackage.eResource();
        var globalScope = getGlobalScope(resource, reference);

        var resourceDescription = globalResourceDescriptionProvider.getResourceDescription(resource);
        var packageName = qualifiedNameProvider.getFullyQualifiedName(gammaModelPackage);
        var resourceSelectable = new TrimPrefixSelectable(resourceDescription, packageName);
        return SelectableBasedScope.createScope(globalScope, resourceSelectable, reference.getEReferenceType(), isIgnoreCase(reference));
    }

    protected IScope getLocalScope(IScope containerScope, EObject context) {
        if (context instanceof State state) {
            return Scopes.scopeFor(state.getRegions(), containerScope);
        }

        if (context instanceof Region region) {
            return Scopes.scopeFor(region.eContents(), containerScope);
        }

        if (context instanceof ComponentDeclaration componentDeclaration) {
            return Scopes.scopeFor(componentDeclaration.eContents(), containerScope);
        }

        if (context instanceof VerificationCaseDeclaration verificationCaseDeclaration) {
            return Scopes.scopeFor(verificationCaseDeclaration.eContents(), containerScope);
        }

        return containerScope;
    }

}
