/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.scoping;


import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ChainingExpression;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ElementReferenceExpression;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaPackage;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Package;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.Scopes;

import static hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaUtils.getAccessibleElements;

/**
 * This class contains custom scoping description.
 * 
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#scoping
 * on how and when to use it.
 */
public class GammaScopeProvider extends AbstractGammaScopeProvider {

    private boolean isTypeReference(EReference reference) {
        return reference == GammaPackage.Literals.COMPONENT_INSTANCE__COMPONENT;
    }

    @Override
    public IScope getScope(EObject context, EReference reference) {
        if (isTypeReference(reference)) {
            var _package = EcoreUtil2.getContainerOfType(context, Package.class);
            return scopeElement(_package, reference);
        }

        if (context instanceof ElementReferenceExpression elementReferenceExpression) {
            return calculateChainScope(elementReferenceExpression, reference);
        }

        return scopeElement(context, reference);
    }

    IScope calculateChainScope(ElementReferenceExpression expression, EReference ref) {
        var chainingExpression = (ChainingExpression) expression.eContainer();
        var index = chainingExpression.getElements().indexOf(expression);

        if (index > 0) {
            var lastReference = chainingExpression.getElements().get(index - 1);
            var referencedElement = lastReference.getElement();
            return scopeElement(referencedElement, ref);
        }

        return scopeElement(chainingExpression, ref);
    }

    protected IScope scopeElement(EObject element, EReference reference) {
        var referenceClass = reference.getEReferenceType().getInstanceClass();
        var accessibleElements = getAccessibleElements(element).filter(referenceClass::isInstance).toList();

        return Scopes.scopeFor(accessibleElements, super.getScope(element, reference));
    }

}
