/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.validation;

import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ActiveStateAssertion;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.BooleanType;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ElementReferenceExpression;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventDeclaration;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ExecutionTrace;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Expression;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.FiredTransitionAssertion;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaPackage;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaUtils;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.LiteralBoolean;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.NavigationSuffixExpression;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.PortDeclaration;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RaiseEventAct;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RaisedEventAssertion;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VariableDeclaration;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VariableValueAssertion;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.validation.Check;

public class GammaValidator extends AbstractGammaValidator {

    public static final String INVALID_TRACE_PATH = "Gamma.INVALID_TRACE_PATH";
    public static final String INVALID_TRACE_EVENT = "Gamma.INVALID_TRACE_EVENT";
    public static final String INVALID_TRACE_VALUE = "Gamma.INVALID_TRACE_VALUE";
    public static final String UNKNOWN_TRACE_TRANSITION = "Gamma.UNKNOWN_TRACE_TRANSITION";
    public static final String AMBIGUOUS_TRACE_TRANSITION = "Gamma.AMBIGUOUS_TRACE_TRANSITION";

    @Check
    public void checkTracePathsStartAtTheTracedInstance(ExecutionTrace executionTrace) {
        var verificationCase = executionTrace.getVerificationCase();
        if (verificationCase == null || verificationCase.eIsProxy()) {
            return;
        }
        var instance = verificationCase.getComponent();
        for (var path : EcoreUtil2.getAllContentsOfType(executionTrace, ElementReferenceExpression.class)) {
            var element = path.getElement();
            if (element != null && !element.eIsProxy() && element != instance) {
                error(
                        "A trace path starts at '" + instance.getName()
                                + "', the component instance of the traced case",
                        path,
                        GammaPackage.Literals.ELEMENT_REFERENCE_EXPRESSION__ELEMENT,
                        INVALID_TRACE_PATH);
            }
        }
    }

    @Check
    public void checkRaiseEventAct(RaiseEventAct raiseEventAct) {
        checkEventPath(raiseEventAct.getEvent(), true, GammaPackage.Literals.RAISE_EVENT_ACT__EVENT);
    }

    @Check
    public void checkRaisedEventAssertion(RaisedEventAssertion raisedEventAssertion) {
        checkEventPath(raisedEventAssertion.getEvent(), false, GammaPackage.Literals.RAISED_EVENT_ASSERTION__EVENT);
    }

    @Check
    public void checkActiveStateAssertion(ActiveStateAssertion activeStateAssertion) {
        var element = GammaUtils.getPathElement(activeStateAssertion.getState());
        if (isResolved(element) && !GammaPackage.Literals.STATE.isInstance(element)) {
            error("Expected a state", GammaPackage.Literals.ACTIVE_STATE_ASSERTION__STATE, INVALID_TRACE_PATH);
        }
    }

    @Check
    public void checkVariableValueAssertion(VariableValueAssertion variableValueAssertion) {
        var element = GammaUtils.getPathElement(variableValueAssertion.getVariable());
        if (!isResolved(element)) {
            return;
        }
        if (!(element instanceof VariableDeclaration variable)) {
            error("Expected a variable", GammaPackage.Literals.VARIABLE_VALUE_ASSERTION__VARIABLE, INVALID_TRACE_PATH);
            return;
        }
        var isBooleanValue = variableValueAssertion.getValue() instanceof LiteralBoolean;
        if (isBooleanValue != (variable.getType() instanceof BooleanType)) {
            error(
                    "The value does not match the type of '" + variable.getName() + "'",
                    GammaPackage.Literals.VARIABLE_VALUE_ASSERTION__VALUE,
                    INVALID_TRACE_VALUE);
        }
    }

    @Check
    public void checkFiredTransitionAssertion(FiredTransitionAssertion firedTransitionAssertion) {
        var element = GammaUtils.getPathElement(firedTransitionAssertion.getFrom());
        if (!isResolved(element)) {
            return;
        }
        // the validator base class declares its own State
        if (!(element instanceof hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.State from)) {
            error("Expected a state", GammaPackage.Literals.FIRED_TRANSITION_ASSERTION__FROM, INVALID_TRACE_PATH);
            return;
        }
        var to = firedTransitionAssertion.getTo();
        if (!isResolved(to)) {
            return;
        }
        var transitions = GammaUtils.findTransitions(from, to, firedTransitionAssertion.getTrigger());
        if (transitions.isEmpty()) {
            error(
                    "No transition from '" + from.getName() + "' to '" + to.getName() + "' matches",
                    GammaPackage.Literals.FIRED_TRANSITION_ASSERTION__TO,
                    UNKNOWN_TRACE_TRANSITION);
        } else if (transitions.size() > 1) {
            error(
                    "More than one transition from '" + from.getName() + "' to '" + to.getName()
                            + "' matches, name the trigger",
                    GammaPackage.Literals.FIRED_TRANSITION_ASSERTION__TO,
                    AMBIGUOUS_TRACE_TRANSITION);
        }
    }

    private void checkEventPath(Expression path, boolean input, EReference feature) {
        var element = GammaUtils.getPathElement(path);
        if (!isResolved(element)) {
            return;
        }
        if (!(element instanceof EventDeclaration event)
                || !(path instanceof NavigationSuffixExpression navigation)
                || !(GammaUtils.getPathElement(navigation.getPrimary()) instanceof PortDeclaration port)) {
            error("Expected an event of a port", feature, INVALID_TRACE_PATH);
            return;
        }
        if (GammaUtils.isInputEvent(port, event) != input) {
            var direction = input ? "an input" : "an output";
            error(
                    "'" + event.getName() + "' is not " + direction + " event of port '" + port.getName() + "'",
                    feature,
                    INVALID_TRACE_EVENT);
        }
    }

    private boolean isResolved(EObject element) {
        return element != null && !element.eIsProxy();
    }
}
