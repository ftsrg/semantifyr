/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.scoping;

import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.*;
import java.util.List;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.Scopes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GammaScopeProvider extends AbstractGammaScopeProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(GammaScopeProvider.class);

    @Override
    public IScope getScope(EObject context, EReference reference) {
        if (context == null || context.eResource() == null) {
            return IScope.NULLSCOPE;
        }

        try {
            return calculateScope(context, reference);
        } catch (RuntimeException e) {
            LOGGER.error("Scoping {} failed", reference.getName(), e);
            return IScope.NULLSCOPE;
        }
    }

    protected IScope calculateScope(EObject context, EReference reference) {
        if (reference == GammaPackage.Literals.TRANSITION__FROM || reference == GammaPackage.Literals.TRANSITION__TO) {
            var statechart = EcoreUtil2.getContainerOfType(context, StatechartDeclaration.class);
            return Scopes.scopeFor(EcoreUtil2.getAllContentsOfType(statechart, StateNode.class));
        }

        if (reference == GammaPackage.Literals.EVENT_TRIGGER__EVENT && context instanceof EventTrigger trigger) {
            return Scopes.scopeFor(GammaUtils.getEvents(trigger.getPort()));
        }

        if (reference == GammaPackage.Literals.RAISE_EVENT_ACTION__EVENT && context instanceof RaiseEventAction action) {
            return Scopes.scopeFor(GammaUtils.getEvents(action.getPort()));
        }

        if (reference == GammaPackage.Literals.RAISE_EVENT_ACT__EVENT && context instanceof RaiseEventAct act) {
            return Scopes.scopeFor(GammaUtils.getEvents(act.getPort()));
        }

        if (context instanceof EventParameterReferenceExpression parameterReference) {
            if (reference == GammaPackage.Literals.EVENT_PARAMETER_REFERENCE_EXPRESSION__EVENT) {
                return Scopes.scopeFor(GammaUtils.getEvents(parameterReference.getPort()));
            }
            if (reference == GammaPackage.Literals.EVENT_PARAMETER_REFERENCE_EXPRESSION__PARAMETER) {
                var event = parameterReference.getEvent();
                if (event == null || event.eIsProxy()) {
                    return IScope.NULLSCOPE;
                }
                return Scopes.scopeFor(event.getParameters());
            }
        }

        if (reference == GammaPackage.Literals.RECORD_ACCESS_EXPRESSION__FIELD && context instanceof RecordAccessExpression access) {
            if (GammaUtils.getType(access.getOperand()) instanceof RecordType recordType) {
                return Scopes.scopeFor(recordType.getFields());
            }
            return IScope.NULLSCOPE;
        }

        if (reference == GammaPackage.Literals.INSTANCE_PORT_REFERENCE__PORT && context instanceof InstancePortReference portReference) {
            var instance = portReference.getInstance();
            if (instance == null || instance.eIsProxy() || instance.getComponent() == null) {
                return IScope.NULLSCOPE;
            }
            return Scopes.scopeFor(instance.getComponent().getPorts());
        }

        if (reference == GammaPackage.Literals.COMPONENT_INSTANCE_REFERENCE__INSTANCE && context instanceof ComponentInstanceReference instanceReference) {
            return Scopes.scopeFor(GammaUtils.getInstances(GammaUtils.getParentComponent(instanceReference)));
        }

        if (reference == GammaPackage.Literals.RAISE_EVENT_ACT__PORT) {
            var component = GammaUtils.getTracedComponent(context);
            if (component == null) {
                return IScope.NULLSCOPE;
            }
            return Scopes.scopeFor(component.getPorts());
        }

        if (context instanceof InstanceElementExpression expression) {
            var scope = getInstanceElementScope(expression, reference);
            if (scope != null) {
                return scope;
            }
        }

        var firedTransition = EcoreUtil2.getContainerOfType(context, FiredTransitionExpression.class);
        if (firedTransition != null
                && (reference == GammaPackage.Literals.TIMEOUT_TRIGGER__TIMEOUT || reference == GammaPackage.Literals.EVENT_TRIGGER__PORT)) {
            if (!(GammaUtils.getReferencedComponent(firedTransition) instanceof StatechartDeclaration statechart)) {
                return IScope.NULLSCOPE;
            }
            if (reference == GammaPackage.Literals.TIMEOUT_TRIGGER__TIMEOUT) {
                return Scopes.scopeFor(statechart.getTimeouts());
            }
            return Scopes.scopeFor(statechart.getPorts());
        }

        return super.getScope(context, reference);
    }

    protected IScope getInstanceElementScope(InstanceElementExpression expression, EReference reference) {
        if (reference == GammaPackage.Literals.STATE_REFERENCE_EXPRESSION__STATE && expression instanceof StateReferenceExpression stateReference) {
            var region = stateReference.getRegion();
            if (region == null || region.eIsProxy()) {
                return IScope.NULLSCOPE;
            }
            return Scopes.scopeFor(EcoreUtil2.typeSelect(region.getStateNodes(), State.class));
        }

        if (reference != GammaPackage.Literals.STATE_REFERENCE_EXPRESSION__REGION
                && reference != GammaPackage.Literals.VARIABLE_REFERENCE_EXPRESSION__VARIABLE
                && reference != GammaPackage.Literals.FIRED_TRANSITION_EXPRESSION__TRANSITION
                && reference != GammaPackage.Literals.FIRED_TRANSITION_EXPRESSION__FROM
                && reference != GammaPackage.Literals.FIRED_TRANSITION_EXPRESSION__TO) {
            return null;
        }

        if (!(GammaUtils.getReferencedComponent(expression) instanceof StatechartDeclaration statechart)) {
            return IScope.NULLSCOPE;
        }

        if (reference == GammaPackage.Literals.STATE_REFERENCE_EXPRESSION__REGION) {
            return Scopes.scopeFor(GammaUtils.getAllRegions(statechart));
        }
        if (reference == GammaPackage.Literals.VARIABLE_REFERENCE_EXPRESSION__VARIABLE) {
            return Scopes.scopeFor(statechart.getVariables());
        }
        if (reference == GammaPackage.Literals.FIRED_TRANSITION_EXPRESSION__TRANSITION) {
            return Scopes.scopeFor(statechart.getTransitions());
        }
        return Scopes.scopeFor(List.copyOf(GammaUtils.getAllStates(statechart)));
    }
}
