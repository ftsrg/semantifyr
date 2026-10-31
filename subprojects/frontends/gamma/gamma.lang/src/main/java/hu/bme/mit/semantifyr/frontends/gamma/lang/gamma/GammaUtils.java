/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.gamma;

import com.google.common.collect.Iterables;
import java.util.List;
import org.eclipse.emf.ecore.EObject;

public class GammaUtils {

    public static EObject getPathElement(Expression path) {
        return switch (path) {
            case NavigationSuffixExpression navigation -> navigation.getMember();
            case ElementReferenceExpression reference -> reference.getElement();
            case null, default -> null;
        };
    }

    public static List<StateTransition> findTransitions(State from, State to, Trigger trigger) {
        var region = (Region) from.eContainer();
        return region.getTransitions().stream()
                .filter(StateTransition.class::isInstance)
                .map(StateTransition.class::cast)
                .filter(it -> it.getFrom() == from && it.getTo() == to)
                .filter(it -> trigger == null || isSameTrigger(it.getTrigger(), trigger))
                .toList();
    }

    public static boolean isSameTrigger(Trigger left, Trigger right) {
        if (left instanceof TimeoutTrigger leftTimeout && right instanceof TimeoutTrigger rightTimeout) {
            return leftTimeout.getTimeout() == rightTimeout.getTimeout();
        }
        if (left instanceof EventTrigger leftEvent && right instanceof EventTrigger rightEvent) {
            return leftEvent.getPort() == rightEvent.getPort() && leftEvent.getEvent() == rightEvent.getEvent();
        }
        return false;
    }

    public static boolean isInputEvent(PortDeclaration port, EventDeclaration event) {
        var declaredIn = event.getDirection() == EventDirection.IN;
        if (port.getRealizationMode() == RealizationMode.PROVIDED) {
            return declaredIn;
        }
        return !declaredIn;
    }

    public static Iterable<EObject> getAllMembers(EObject declaration) {
        return switch (declaration) {
            case ComponentInstance decl -> getAllMembers(decl);
            case PortDeclaration decl -> getAllMembers(decl);
            case InterfaceDeclaration decl -> getAllMembers(decl);
            case StatechartDeclaration decl -> getAllMembers(decl);
            case SyncComponentDeclaration decl -> getAllMembers(decl);
            case Region decl -> getAllMembers(decl);
            case State decl -> getAllMembers(decl);
            default -> List.of();
        };
    }

    public static Iterable<EObject> getAllMembers(ComponentInstance declaration) {
        return getAllMembers(declaration.getComponent());
    }

    public static Iterable<EObject> getAllMembers(PortDeclaration declaration) {
        var inter = declaration.getInterface();
        return getAllMembers(inter);
    }

    public static Iterable<EObject> getAllMembers(InterfaceDeclaration declaration) {
        return Iterables.concat(declaration.getEvents());
    }

    public static Iterable<EObject> getAllMembers(StatechartDeclaration declaration) {
        return Iterables.concat(
                declaration.getRegions(),
                declaration.getTimeouts(),
                declaration.getVariables(),
                declaration.getPorts());
    }

    public static Iterable<EObject> getAllMembers(SyncComponentDeclaration declaration) {
        return Iterables.concat(declaration.getPorts(), declaration.getComponents());
    }

    public static Iterable<EObject> getAllMembers(Region declaration) {
        return Iterables.concat(declaration.getStates(), declaration.getTransitions());
    }

    public static Iterable<EObject> getAllMembers(State declaration) {
        return Iterables.concat(declaration.getRegions());
    }
}
