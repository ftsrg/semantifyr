/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.formatting2;

import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ActiveStateAssertion;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ArithmeticUnaryOperator;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventTrigger;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ExecutionTrace;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.FiredTransitionAssertion;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaPackage;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.NavigationSuffixExpression;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RaiseEventAct;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RaisedEventAssertion;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Step;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VariableValueAssertion;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.formatting2.AbstractJavaFormatter;
import org.eclipse.xtext.formatting2.FormatterPreferenceKeys;
import org.eclipse.xtext.formatting2.FormatterRequest;
import org.eclipse.xtext.formatting2.IFormattableDocument;
import org.eclipse.xtext.formatting2.IHiddenRegionFormatter;
import org.eclipse.xtext.preferences.MapBasedPreferenceValues;

@SuppressWarnings("UnstableApiUsage")
public class GammaFormatter extends AbstractJavaFormatter {

    @Override
    protected void initialize(FormatterRequest request) {
        // the Xtext serializer sends no preferences, editors send their own indentation
        if (request.getPreferences() instanceof MapBasedPreferenceValues preferences
                && !preferences.getValues().containsKey(FormatterPreferenceKeys.indentation.getId())) {
            preferences.put(FormatterPreferenceKeys.indentation, "    ");
        }
        super.initialize(request);
    }

    protected void format(GammaModelPackage gammaModelPackage, IFormattableDocument document) {
        document.append(regionFor(gammaModelPackage).keyword("package"), this::oneSpace);
        for (var executionTrace : gammaModelPackage.getTraces()) {
            document.set(previousHiddenRegion(executionTrace), this::twoLines);
            document.format(executionTrace);
        }
        document.set(nextHiddenRegion(gammaModelPackage), this::newLine);
    }

    protected void format(ExecutionTrace executionTrace, IFormattableDocument document) {
        document.append(regionFor(executionTrace).keyword("trace"), this::oneSpace);
        document.surround(regionFor(executionTrace).keyword("of"), this::oneSpace);
        formatBlocks(executionTrace, document);
        for (var step : executionTrace.getSteps()) {
            document.set(previousHiddenRegion(step), this::newLine);
            document.format(step);
        }
    }

    protected void format(Step step, IFormattableDocument document) {
        formatBlocks(step, document);
        document.prepend(regionFor(step).keyword("act"), this::newLine);
        document.prepend(regionFor(step).keyword("assert"), this::newLine);
        for (var act : step.getActs()) {
            document.set(previousHiddenRegion(act), this::newLine);
            document.format(act);
        }
        for (var assertion : step.getAsserts()) {
            document.set(previousHiddenRegion(assertion), this::newLine);
            document.format(assertion);
        }
    }

    protected void format(RaiseEventAct raiseEventAct, IFormattableDocument document) {
        formatPath(raiseEventAct.getEvent(), document);
    }

    protected void format(FiredTransitionAssertion firedTransitionAssertion, IFormattableDocument document) {
        formatPath(firedTransitionAssertion.getFrom(), document);
        document.surround(regionFor(firedTransitionAssertion).keyword("to"), this::oneSpace);
        document.surround(regionFor(firedTransitionAssertion).keyword("when"), this::oneSpace);
        document.format(firedTransitionAssertion.getTrigger());
    }

    protected void format(EventTrigger eventTrigger, IFormattableDocument document) {
        document.surround(regionFor(eventTrigger).keyword("."), this::noSpace);
    }

    protected void format(RaisedEventAssertion raisedEventAssertion, IFormattableDocument document) {
        formatPath(raisedEventAssertion.getEvent(), document);
    }

    protected void format(ActiveStateAssertion activeStateAssertion, IFormattableDocument document) {
        formatPath(activeStateAssertion.getState(), document);
    }

    protected void format(VariableValueAssertion variableValueAssertion, IFormattableDocument document) {
        formatPath(variableValueAssertion.getVariable(), document);
        document.surround(regionFor(variableValueAssertion).keyword("=="), this::oneSpace);
        if (variableValueAssertion.getValue() instanceof ArithmeticUnaryOperator signedValue) {
            document.append(
                    regionFor(signedValue).feature(GammaPackage.Literals.ARITHMETIC_UNARY_OPERATOR__OP), this::noSpace);
        }
    }

    protected void formatPath(EObject path, IFormattableDocument document) {
        document.prepend(path, this::oneSpace);
        var current = path;
        while (current instanceof NavigationSuffixExpression navigation) {
            document.surround(regionFor(navigation).keyword("."), this::noSpace);
            current = navigation.getPrimary();
        }
    }

    protected void formatBlocks(EObject eObject, IFormattableDocument document) {
        for (var block : regionFor(eObject).keywordPairs("{", "}")) {
            document.prepend(block.getKey(), this::oneSpace);
            document.prepend(block.getValue(), this::newLine);
            document.interior(block, this::indent);
        }
    }

    protected void twoLines(IHiddenRegionFormatter hiddenRegionFormatter) {
        hiddenRegionFormatter.setNewLines(2);
    }
}
