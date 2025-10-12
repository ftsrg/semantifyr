/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.xsts.lang.scoping;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.xsts.lang.xsts.SequenceOperation;
import hu.bme.mit.semantifyr.xsts.lang.xsts.XstsModel;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.Scopes;
import org.eclipse.xtext.scoping.impl.AbstractGlobalScopeDelegatingScopeProvider;
import org.eclipse.xtext.scoping.impl.GlobalResourceDescriptionProvider;
import org.eclipse.xtext.scoping.impl.SelectableBasedScope;

public class XstsLocalScopeProvider extends AbstractGlobalScopeDelegatingScopeProvider {

    @Inject
    private GlobalResourceDescriptionProvider globalResourceDescriptionProvider;

    @Override
    public IScope getScope(EObject child, EReference reference) {
        var context = child.eContainer();

        if (context instanceof XstsModel xstsModel) {
            return getXstsModelScope(xstsModel, reference);
        }

        var containerScope = getScope(context, reference);
        return getLocalScope(containerScope, context, child);
    }

    protected IScope getXstsModelScope(XstsModel xstsModel, EReference reference) {
        var resource = xstsModel.eResource();
        var globalScope = getGlobalScope(resource, reference);

        var resourceDescription = globalResourceDescriptionProvider.getResourceDescription(resource);
        return SelectableBasedScope.createScope(globalScope, resourceDescription, reference.getEReferenceType(), isIgnoreCase(reference));
    }

    protected IScope getLocalScope(IScope containerScope, EObject context, EObject child) {
        if (context instanceof SequenceOperation operation) {
            var index = operation.getSteps().indexOf(child);
            return Scopes.scopeFor(operation.eContents().subList(0, index), containerScope);
        }

        return containerScope;
    }

}
