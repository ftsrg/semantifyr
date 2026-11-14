/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.gamma;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.EcoreUtil2;

public class GammaUtils {

    public static List<Transition> findTransitions(State from, State to, Trigger trigger) {
        var statechart = EcoreUtil2.getContainerOfType(from, StatechartDeclaration.class);
        return statechart.getTransitions().stream()
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
        return left == null && right == null;
    }

    public static boolean isInputEvent(PortDeclaration port, EventDeclaration event) {
        if (event.getDirection() == EventDirection.INOUT) {
            return true;
        }
        var declaredIn = event.getDirection() == EventDirection.IN;
        if (port.getRealizationMode() == RealizationMode.PROVIDED) {
            return declaredIn;
        }
        return !declaredIn;
    }

    public static boolean isOutputEvent(PortDeclaration port, EventDeclaration event) {
        if (event.getDirection() == EventDirection.INOUT) {
            return true;
        }
        return !isInputEvent(port, event);
    }

    public static List<EventDeclaration> getAllEvents(InterfaceDeclaration interfaceDeclaration) {
        var interfaces = new LinkedHashSet<InterfaceDeclaration>();
        collectInterfaces(interfaceDeclaration, interfaces);
        var events = new ArrayList<EventDeclaration>();
        for (var collected : interfaces) {
            events.addAll(collected.getEvents());
        }
        return events;
    }

    private static void collectInterfaces(
            InterfaceDeclaration interfaceDeclaration, Set<InterfaceDeclaration> interfaces) {
        if (interfaceDeclaration == null || interfaceDeclaration.eIsProxy() || !interfaces.add(interfaceDeclaration)) {
            return;
        }
        for (var parent : interfaceDeclaration.getParents()) {
            collectInterfaces(parent, interfaces);
        }
    }

    public static List<EventDeclaration> getEvents(PortDeclaration port) {
        if (port == null || port.eIsProxy()) {
            return List.of();
        }
        return getAllEvents(port.getInterface());
    }

    public static List<Region> getAllRegions(StatechartDeclaration statechart) {
        return EcoreUtil2.getAllContentsOfType(statechart, Region.class);
    }

    public static List<State> getAllStates(StatechartDeclaration statechart) {
        return EcoreUtil2.getAllContentsOfType(statechart, State.class);
    }

    public static ComponentDeclaration getTracedComponent(EObject element) {
        var verificationCase = EcoreUtil2.getContainerOfType(element, VerificationCaseDeclaration.class);
        if (verificationCase == null) {
            var executionTrace = EcoreUtil2.getContainerOfType(element, ExecutionTrace.class);
            if (executionTrace == null) {
                return null;
            }
            verificationCase = executionTrace.getVerificationCase();
        }
        if (verificationCase == null || verificationCase.eIsProxy()) {
            return null;
        }
        return resolved(verificationCase.getComponent());
    }

    public static ComponentDeclaration getReferencedComponent(InstanceElementExpression expression) {
        if (expression.getInstance() == null) {
            return getTracedComponent(expression);
        }
        return getReferencedComponent(expression.getInstance());
    }

    public static ComponentDeclaration getReferencedComponent(ComponentInstanceReference reference) {
        var current = reference;
        while (current.getChild() != null) {
            current = current.getChild();
        }
        var instance = current.getInstance();
        if (instance == null || instance.eIsProxy()) {
            return null;
        }
        return resolved(instance.getComponent());
    }

    public static ComponentDeclaration getParentComponent(ComponentInstanceReference reference) {
        if (reference.eContainer() instanceof ComponentInstanceReference parent) {
            var instance = parent.getInstance();
            if (instance == null || instance.eIsProxy()) {
                return null;
            }
            return resolved(instance.getComponent());
        }
        return getTracedComponent(reference);
    }

    public static List<ComponentInstance> getInstances(ComponentDeclaration component) {
        if (component instanceof CompositeComponentDeclaration composite) {
            return composite.getComponents();
        }
        if (component instanceof AsynchronousAdapterDeclaration adapter && adapter.getWrappedComponent() != null) {
            return List.of(adapter.getWrappedComponent());
        }
        return List.of();
    }

    // an adapter offers its wrapped component's ports beside its own, as Gamma's adapter does
    public static List<PortDeclaration> getAllPorts(ComponentDeclaration component) {
        if (component == null) {
            return List.of();
        }
        if (!(component instanceof AsynchronousAdapterDeclaration adapter)) {
            return component.getPorts();
        }
        var ports = new ArrayList<PortDeclaration>(adapter.getPorts());
        var wrapped = adapter.getWrappedComponent();
        if (wrapped != null && resolved(wrapped.getComponent()) != null) {
            ports.addAll(wrapped.getComponent().getPorts());
        }
        return ports;
    }

    public static boolean isAsynchronous(ComponentDeclaration component) {
        return component instanceof AsynchronousAdapterDeclaration
                || component instanceof AsynchronousCompositeComponentDeclaration
                || component instanceof ScheduledAsynchronousCompositeComponentDeclaration
                || (component instanceof StatechartDeclaration statechart && statechart.isAsynchronous());
    }

    public static List<EventDeclaration> getInputEvents(PortDeclaration port) {
        return getEvents(port).stream().filter(it -> isInputEvent(port, it)).toList();
    }

    public static List<EventDeclaration> getOutputEvents(PortDeclaration port) {
        return getEvents(port).stream().filter(it -> isOutputEvent(port, it)).toList();
    }

    public static List<ComponentInstance> getInstancePath(InstanceElementExpression expression) {
        var instances = new ArrayList<ComponentInstance>();
        var current = expression.getInstance();
        while (current != null) {
            instances.add(current.getInstance());
            current = current.getChild();
        }
        return instances;
    }

    public static Type getType(Expression expression) {
        var type = switch (expression) {
            case DirectReferenceExpression reference
            when reference.getDeclaration() != null ->
                reference.getDeclaration().getType();
            case EventParameterReferenceExpression reference
            when reference.getParameter() != null -> reference.getParameter().getType();
            case VariableReferenceExpression reference
            when reference.getVariable() != null -> reference.getVariable().getType();
            case RecordAccessExpression access
            when access.getField() != null -> access.getField().getType();
            case null, default -> null;
        };
        return resolveType(type);
    }

    public static Integer evaluateInteger(Expression expression) {
        return switch (expression) {
            case LiteralInteger literal -> literal.getValue();
            case ArithmeticUnaryOperator unary -> {
                var body = evaluateInteger(unary.getBody());
                if (body == null) {
                    yield null;
                }
                if (unary.getOp() == UnaryOp.MINUS) {
                    yield -body;
                }
                yield body;
            }
            case ArithmeticBinaryOperator binary -> {
                var left = evaluateInteger(binary.getLeft());
                var right = evaluateInteger(binary.getRight());
                if (left == null || right == null || (binary.getOp() == ArithmeticOp.DIV && right == 0)) {
                    yield null;
                }
                yield switch (binary.getOp()) {
                    case ADD -> left + right;
                    case SUB -> left - right;
                    case MUL -> left * right;
                    case DIV -> left / right;
                };
            }
            case null, default -> null;
        };
    }

    public static Type resolveType(Type type) {
        var current = type;
        var visited = new LinkedHashSet<TypeDeclaration>();
        while (current instanceof TypeReference reference
                && reference.getReference() != null
                && !reference.getReference().eIsProxy()
                && visited.add(reference.getReference())) {
            current = reference.getReference().getType();
        }
        return current;
    }

    private static <T extends EObject> T resolved(T element) {
        if (element == null || element.eIsProxy()) {
            return null;
        }
        return element;
    }
}
