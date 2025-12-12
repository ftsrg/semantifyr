/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.scoping;

import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.*;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.Scopes;

public class GammaScopeProvider extends AbstractGammaScopeProvider {

    private boolean isTypeReference(EReference reference) {
        return reference == GammaPackage.Literals.COMPONENT_INSTANCE__COMPONENT;
    }

    @Override
    public IScope getScope(EObject context, EReference reference) {
        if (context == null || context.eResource() == null) {
            return IScope.NULLSCOPE;
        }

        try {
            return calculateScope(context, reference);
        } catch (RuntimeException e) {
            // FIXME: should log here
            e.printStackTrace();
            return IScope.NULLSCOPE;
        }
    }

    protected IScope calculateScope(EObject context, EReference reference) {
        if (isTypeReference(reference)) {
            return super.getScope(context, reference);
        }

        if (context instanceof NavigationSuffixExpression navigationSuffixExpression) {
            var primary = navigationSuffixExpression.getPrimary();

            EObject element;
            if (primary instanceof NavigationSuffixExpression suffixExpression) {
                element = suffixExpression.getMember();
            } else if (primary instanceof ElementReferenceExpression elementReferenceExpression) {
                element = elementReferenceExpression.getElement();
            } else {
                return IScope.NULLSCOPE;
            }

            var primaryMembers = GammaUtils.getAllMembers(element);

            return Scopes.scopeFor(primaryMembers, IScope.NULLSCOPE);
        }

        if (context instanceof InstancePortReference instancePortReference && reference == GammaPackage.Literals.INSTANCE_PORT_REFERENCE__PORT) {
            var instance = instancePortReference.getInstance();

            return Scopes.scopeFor(instance.getComponent().getPorts(), IScope.NULLSCOPE);
        }

        if (context instanceof EventTrigger eventTrigger && reference == GammaPackage.Literals.EVENT_TRIGGER__EVENT) {
            var port = eventTrigger.getPort();

            return Scopes.scopeFor(port.getInterface().getEvents(), IScope.NULLSCOPE);
        }

        if (context instanceof RaiseEventAction raiseEventAction && reference == GammaPackage.Literals.RAISE_EVENT_ACTION__EVENT) {
            var port = raiseEventAction.getPort();

            return Scopes.scopeFor(port.getInterface().getEvents(), IScope.NULLSCOPE);
        }

        return super.getScope(context, reference);
    }

}
