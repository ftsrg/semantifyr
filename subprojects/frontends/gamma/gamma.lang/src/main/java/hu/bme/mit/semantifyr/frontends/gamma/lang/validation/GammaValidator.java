/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.validation;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.linking.impl.LinkingHelper;
import org.eclipse.xtext.naming.IQualifiedNameConverter;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.impl.ResourceDescriptionsProvider;
import org.eclipse.xtext.util.Strings;
import org.eclipse.xtext.validation.Check;

public class GammaValidator extends AbstractGammaValidator {

    public static final String INVALID_FILE_CONTENT = "Gamma.INVALID_FILE_CONTENT";
    public static final String AMBIGUOUS_REFERENCE = "Gamma.AMBIGUOUS_REFERENCE";
    public static final String TRIGGERLESS_TRANSITION = "Gamma.TRIGGERLESS_TRANSITION";
    public static final String TIMEOUT_SET_TO_DIFFERENT_VALUES = "Gamma.TIMEOUT_SET_TO_DIFFERENT_VALUES";
    public static final String NON_POSITIVE_TIME = "Gamma.NON_POSITIVE_TIME";
    public static final String DUPLICATE_STATE_NAME = "Gamma.DUPLICATE_STATE_NAME";
    public static final String INDISTINGUISHABLE_TRANSITIONS = "Gamma.INDISTINGUISHABLE_TRANSITIONS";
    public static final String INEFFECTIVE_PRIORITY = "Gamma.INEFFECTIVE_PRIORITY";
    public static final String INVALID_TRACE_EVENT = "Gamma.INVALID_TRACE_EVENT";
    public static final String INVALID_TRACE_VALUE = "Gamma.INVALID_TRACE_VALUE";
    public static final String UNKNOWN_TRACE_TRANSITION = "Gamma.UNKNOWN_TRACE_TRANSITION";
    public static final String AMBIGUOUS_TRACE_TRANSITION = "Gamma.AMBIGUOUS_TRACE_TRANSITION";

    private static final String TRACE_FILE_EXTENSION = "get";

    @Inject
    private IQualifiedNameProvider qualifiedNameProvider;

    @Inject
    private IQualifiedNameConverter qualifiedNameConverter;

    @Inject
    private ResourceDescriptionsProvider resourceDescriptionsProvider;

    @Inject
    private LinkingHelper linkingHelper;

    @Check
    public void checkFileContent(GammaRoot gammaRoot) {
        var resource = gammaRoot.eResource();
        if (resource == null || resource.getURI() == null) {
            return;
        }
        var isTraceFile = TRACE_FILE_EXTENSION.equals(resource.getURI().fileExtension());
        if (isTraceFile && gammaRoot instanceof GammaModelPackage) {
            error(
                    "A .get file holds an execution trace",
                    gammaRoot,
                    GammaPackage.Literals.NAMED_ELEMENT__NAME,
                    INVALID_FILE_CONTENT);
        }
        if (!isTraceFile && gammaRoot instanceof ExecutionTrace) {
            error(
                    "An execution trace is a .get file of its own",
                    gammaRoot,
                    GammaPackage.Literals.NAMED_ELEMENT__NAME,
                    INVALID_FILE_CONTENT);
        }
    }

    @Check
    public void checkAmbiguousImport(EObject element) {
        if (!(EcoreUtil2.getRootContainer(element) instanceof GammaRoot gammaRoot)
                || gammaRoot.getImports().size() < 2) {
            return;
        }
        for (var reference : element.eClass().getEAllReferences()) {
            if (reference.isContainment()
                    || reference.isContainer()
                    || reference.isMany()
                    || reference == GammaPackage.Literals.IMPORT_STATEMENT__IMPORTED_PACKAGE) {
                continue;
            }
            if (!(element.eGet(reference, false) instanceof EObject target)
                    || target.eIsProxy()
                    || !(target.eContainer() instanceof GammaModelPackage targetPackage)
                    || targetPackage == gammaRoot) {
                continue;
            }
            var nodes = NodeModelUtils.findNodesForFeature(element, reference);
            if (nodes.isEmpty()) {
                continue;
            }
            var text = linkingHelper.getCrossRefNodeAsString(nodes.getFirst(), true);
            if (Strings.isEmpty(text)) {
                continue;
            }
            var candidates = getImportedCandidates(gammaRoot, reference, qualifiedNameConverter.toQualifiedName(text));
            if (candidates.size() < 2 || !candidates.contains(qualifiedNameProvider.getFullyQualifiedName(target))) {
                continue;
            }
            var names =
                    candidates.stream().map(qualifiedNameConverter::toString).toList();
            error(
                    "Ambiguous reference '%s': the imports declare %s".formatted(text, String.join(" and ", names)),
                    element,
                    reference,
                    AMBIGUOUS_REFERENCE);
        }
    }

    private LinkedHashSet<QualifiedName> getImportedCandidates(
            GammaRoot gammaRoot, EReference reference, QualifiedName name) {
        var index = resourceDescriptionsProvider.getResourceDescriptions(gammaRoot.eResource());
        var candidates = new LinkedHashSet<QualifiedName>();
        for (var importStatement : gammaRoot.getImports()) {
            var importedPackage = importStatement.getImportedPackage();
            if (importedPackage == null || importedPackage.eIsProxy()) {
                continue;
            }
            var importedName = qualifiedNameProvider.getFullyQualifiedName(importedPackage);
            for (var description :
                    index.getExportedObjects(reference.getEReferenceType(), importedName.append(name), false)) {
                candidates.add(description.getQualifiedName());
            }
        }
        return candidates;
    }

    @Check
    public void checkTriggerlessTransition(Transition transition) {
        // the validator base class declares its own State
        if (transition.getFrom() instanceof hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.State
                && transition.getTrigger() == null) {
            error(
                    "A transition leaving a state needs a trigger",
                    transition,
                    GammaPackage.Literals.TRANSITION__FROM,
                    TRIGGERLESS_TRANSITION);
        }
    }

    @Check
    public void checkTimeoutSetToOneValue(StatechartDeclaration statechart) {
        var settings = new LinkedHashMap<TimeoutDeclaration, List<SetTimeoutAction>>();
        for (var action : EcoreUtil2.getAllContentsOfType(statechart, SetTimeoutAction.class)) {
            if (action.getTimeout() != null && !action.getTimeout().eIsProxy()) {
                settings.computeIfAbsent(action.getTimeout(), ignored -> new ArrayList<>())
                        .add(action);
            }
        }
        for (var entry : settings.entrySet()) {
            var actions = entry.getValue();
            var first = actions.getFirst();
            var allEqual = actions.stream()
                    .allMatch(
                            it -> it.getUnit() == first.getUnit() && EcoreUtil.equals(it.getValue(), first.getValue()));
            if (allEqual) {
                continue;
            }
            for (var action : actions) {
                error(
                        "Timeout '" + entry.getKey().getName() + "' is set to different values",
                        action,
                        GammaPackage.Literals.SET_TIMEOUT_ACTION__TIMEOUT,
                        TIMEOUT_SET_TO_DIFFERENT_VALUES);
            }
        }
    }

    @Check
    public void checkPositiveTime(SetTimeoutAction action) {
        var value = evaluateInteger(action.getValue());
        if (value != null && value <= 0) {
            error(
                    "A time value must be positive, it is " + value,
                    action,
                    GammaPackage.Literals.SET_TIMEOUT_ACTION__VALUE,
                    NON_POSITIVE_TIME);
        }
    }

    private Integer evaluateInteger(Expression expression) {
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

    @Check
    public void checkStateNameUniqueness(StatechartDeclaration statechart) {
        var states = new LinkedHashMap<String, List<hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.State>>();
        for (var state : GammaUtils.getAllStates(statechart)) {
            if (state.getName() != null) {
                states.computeIfAbsent(state.getName(), ignored -> new ArrayList<>())
                        .add(state);
            }
        }
        for (var entry : states.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            for (var state : entry.getValue()) {
                error(
                        "Duplicate state name '" + entry.getKey() + "' in statechart '" + statechart.getName() + "'",
                        state,
                        GammaPackage.Literals.NAMED_ELEMENT__NAME,
                        DUPLICATE_STATE_NAME);
            }
        }
    }

    @Check
    public void checkTransitionsDistinguishable(StatechartDeclaration statechart) {
        var transitions = statechart.getTransitions();
        for (var transition : transitions) {
            if (transition.getName() != null) {
                continue;
            }
            var hasTwin = transitions.stream()
                    .anyMatch(other -> other != transition
                            && other.getFrom() == transition.getFrom()
                            && other.getTo() == transition.getTo()
                            && GammaUtils.isSameTrigger(other.getTrigger(), transition.getTrigger()));
            if (hasTwin) {
                warning(
                        "Another transition shares this one's source, target and trigger, name it with @(id)",
                        transition,
                        GammaPackage.Literals.TRANSITION__FROM,
                        INDISTINGUISHABLE_TRANSITIONS);
            }
        }
    }

    @Check
    public void checkPriorityHasEffect(Transition transition) {
        var statechart = EcoreUtil2.getContainerOfType(transition, StatechartDeclaration.class);
        if (statechart == null || statechart.getTransitionPriority() != TransitionPriority.OFF) {
            return;
        }
        if (!NodeModelUtils.findNodesForFeature(transition, GammaPackage.Literals.TRANSITION__PRIORITY)
                .isEmpty()) {
            warning(
                    "The transition priority of '" + statechart.getName() + "' is off, so this priority has no effect",
                    transition,
                    GammaPackage.Literals.TRANSITION__PRIORITY,
                    INEFFECTIVE_PRIORITY);
        }
    }

    @Check
    public void checkRaiseEventAct(RaiseEventAct raiseEventAct) {
        var port = raiseEventAct.getPort();
        var event = raiseEventAct.getEvent();
        if (port == null || port.eIsProxy() || event == null || event.eIsProxy()) {
            return;
        }
        if (raiseEventAct.eContainingFeature() == GammaPackage.Literals.STEP__ACTS) {
            if (!GammaUtils.isInputEvent(port, event)) {
                error(
                        "'" + event.getName() + "' is not an input event of port '" + port.getName() + "'",
                        GammaPackage.Literals.RAISE_EVENT_ACT__EVENT,
                        INVALID_TRACE_EVENT);
            }
        } else if (!GammaUtils.isOutputEvent(port, event)) {
            error(
                    "'" + event.getName() + "' is not an output event of port '" + port.getName() + "'",
                    GammaPackage.Literals.RAISE_EVENT_ACT__EVENT,
                    INVALID_TRACE_EVENT);
        }
    }

    @Check
    public void checkVariableValue(ComparisonOperator comparison) {
        if (!(comparison.getLeft() instanceof VariableReferenceExpression reference)
                || !(comparison.getRight() instanceof LiteralExpression
                        || comparison.getRight() instanceof ArithmeticUnaryOperator)) {
            return;
        }
        var variable = reference.getVariable();
        if (variable == null || variable.eIsProxy()) {
            return;
        }
        var type = GammaUtils.resolveType(variable.getType());
        var isBooleanValue = comparison.getRight() instanceof LiteralBoolean;
        if (isBooleanValue != (type instanceof BooleanType)) {
            error(
                    "The value does not match the type of '" + variable.getName() + "'",
                    GammaPackage.Literals.COMPARISON_OPERATOR__RIGHT,
                    INVALID_TRACE_VALUE);
        }
    }

    @Check
    public void checkFiredTransition(FiredTransitionExpression firedTransition) {
        var from = firedTransition.getFrom();
        var to = firedTransition.getTo();
        if (from == null || from.eIsProxy() || to == null || to.eIsProxy()) {
            return;
        }
        var transitions = GammaUtils.findTransitions(from, to, firedTransition.getTrigger());
        if (transitions.isEmpty()) {
            error(
                    "No transition from '" + from.getName() + "' to '" + to.getName() + "' matches",
                    GammaPackage.Literals.FIRED_TRANSITION_EXPRESSION__TO,
                    UNKNOWN_TRACE_TRANSITION);
        } else if (transitions.size() > 1) {
            error(
                    "More than one transition from '" + from.getName() + "' to '" + to.getName()
                            + "' matches, name the trigger or the transition",
                    GammaPackage.Literals.FIRED_TRANSITION_EXPRESSION__TO,
                    AMBIGUOUS_TRACE_TRANSITION);
        }
    }
}
