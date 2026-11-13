/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.formatting2;

import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ArithmeticUnaryOperator;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.BooleanOperator;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComparisonOperator;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComponentInstanceReference;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventTrigger;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ExecutionTrace;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.FiredTransitionExpression;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaPackage;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ImportStatement;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.NegationOperator;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RaiseEventAct;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StateReferenceExpression;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Step;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TimeElapseAct;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VariableReferenceExpression;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.formatting2.AbstractJavaFormatter;
import org.eclipse.xtext.formatting2.IFormattableDocument;
import org.eclipse.xtext.formatting2.IHiddenRegionFormatter;

@SuppressWarnings("UnstableApiUsage")
public class GammaFormatter extends AbstractJavaFormatter {

    protected void format(GammaModelPackage gammaModelPackage, IFormattableDocument document) {
        document.append(regionFor(gammaModelPackage).keyword("package"), this::oneSpace);
        for (var importStatement : gammaModelPackage.getImports()) {
            document.set(previousHiddenRegion(importStatement), this::newLine);
            document.format(importStatement);
        }
        document.set(nextHiddenRegion(gammaModelPackage), this::newLine);
    }

    protected void format(ExecutionTrace executionTrace, IFormattableDocument document) {
        for (var importStatement : executionTrace.getImports()) {
            if (importStatement != executionTrace.getImports().getFirst()) {
                document.set(previousHiddenRegion(importStatement), this::newLine);
            }
            document.format(importStatement);
        }
        var traceKeyword = regionFor(executionTrace).keyword("trace");
        if (!executionTrace.getImports().isEmpty()) {
            document.prepend(traceKeyword, this::twoLines);
        }
        document.append(traceKeyword, this::oneSpace);
        document.surround(regionFor(executionTrace).keyword("of"), this::oneSpace);
        var first = true;
        for (var step : executionTrace.getSteps()) {
            if (first) {
                document.set(previousHiddenRegion(step), this::twoLines);
            } else {
                document.set(previousHiddenRegion(step), this::newLine);
            }
            first = false;
            document.format(step);
        }
        document.set(nextHiddenRegion(executionTrace), this::newLine);
    }

    protected void format(ImportStatement importStatement, IFormattableDocument document) {
        document.append(regionFor(importStatement).keyword("import"), this::oneSpace);
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
        for (var semicolon : regionFor(step).keywords(";")) {
            document.prepend(semicolon, this::noSpace);
        }
    }

    protected void format(TimeElapseAct timeElapseAct, IFormattableDocument document) {
        document.append(regionFor(timeElapseAct).keyword("elapse"), this::oneSpace);
    }

    protected void format(RaiseEventAct raiseEventAct, IFormattableDocument document) {
        document.append(regionFor(raiseEventAct).keyword("raise"), this::oneSpace);
        formatNavigation(raiseEventAct, document);
        document.format(raiseEventAct.getInstance());
    }

    protected void format(StateReferenceExpression stateReference, IFormattableDocument document) {
        document.append(regionFor(stateReference).keyword("state"), this::oneSpace);
        formatNavigation(stateReference, document);
        document.format(stateReference.getInstance());
    }

    protected void format(VariableReferenceExpression variableReference, IFormattableDocument document) {
        document.append(regionFor(variableReference).keyword("variable"), this::oneSpace);
        formatNavigation(variableReference, document);
        document.format(variableReference.getInstance());
    }

    protected void format(FiredTransitionExpression firedTransition, IFormattableDocument document) {
        document.append(regionFor(firedTransition).keyword("transition"), this::oneSpace);
        formatNavigation(firedTransition, document);
        document.surround(regionFor(firedTransition).keyword("from"), this::oneSpace);
        document.surround(regionFor(firedTransition).keyword("to"), this::oneSpace);
        document.surround(regionFor(firedTransition).keyword("when"), this::oneSpace);
        document.format(firedTransition.getInstance());
        document.format(firedTransition.getTrigger());
    }

    protected void format(ComponentInstanceReference instanceReference, IFormattableDocument document) {
        document.surround(regionFor(instanceReference).keyword("->"), this::noSpace);
        document.format(instanceReference.getChild());
    }

    protected void format(EventTrigger eventTrigger, IFormattableDocument document) {
        formatNavigation(eventTrigger, document);
    }

    protected void format(ComparisonOperator comparison, IFormattableDocument document) {
        document.surround(regionFor(comparison).feature(GammaPackage.Literals.COMPARISON_OPERATOR__OP), this::oneSpace);
        document.format(comparison.getLeft());
        document.format(comparison.getRight());
    }

    protected void format(BooleanOperator booleanOperator, IFormattableDocument document) {
        document.surround(
                regionFor(booleanOperator).feature(GammaPackage.Literals.BOOLEAN_OPERATOR__OP), this::oneSpace);
        document.format(booleanOperator.getLeft());
        document.format(booleanOperator.getRight());
    }

    protected void format(NegationOperator negation, IFormattableDocument document) {
        document.format(negation.getBody());
    }

    protected void format(ArithmeticUnaryOperator unary, IFormattableDocument document) {
        document.append(regionFor(unary).feature(GammaPackage.Literals.ARITHMETIC_UNARY_OPERATOR__OP), this::noSpace);
        document.format(unary.getBody());
    }

    protected void formatNavigation(EObject eObject, IFormattableDocument document) {
        for (var dot : regionFor(eObject).keywords(".")) {
            document.surround(dot, this::noSpace);
        }
        for (var open : regionFor(eObject).keywords("(")) {
            document.surround(open, this::noSpace);
        }
        for (var close : regionFor(eObject).keywords(")")) {
            document.prepend(close, this::noSpace);
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
