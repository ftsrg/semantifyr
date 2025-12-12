/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.xsts.lang.formatting2;

import hu.bme.mit.semantifyr.xsts.lang.xsts.*;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.formatting2.AbstractJavaFormatter;
import org.eclipse.xtext.formatting2.IFormattableDocument;
import org.eclipse.xtext.formatting2.IHiddenRegionFormatter;

@SuppressWarnings("UnstableApiUsage")
public class XstsFormatter extends AbstractJavaFormatter {

    protected void formatBracketedDeclaration(EObject declaration, IFormattableDocument document) {
        formatSemicolonedLine(declaration, document);
        formatBrackets(declaration, document);
    }

    protected void formatSemicolonedLine(EObject declaration, IFormattableDocument document) {
        document.prepend(regionFor(declaration).keyword(";"), this::noSpace);
        document.set(previousHiddenRegion(declaration), this::newLines);
        document.set(nextHiddenRegion(declaration), this::newLines);
    }

    protected void formatBrackets(EObject eObject, IFormattableDocument document) {
        document.prepend(regionFor(eObject).keyword("{"), this::oneSpace);
        document.append(regionFor(eObject).keyword("{"), this::newLines);
        document.prepend(regionFor(eObject).keyword("}"), this::newLines);
        document.interior(regionFor(eObject).keyword("{"), regionFor(eObject).keyword("}"), this::indent);
    }

    protected void newLines(IHiddenRegionFormatter hrf) {
        hrf.setNewLines(1, 1, 2);
    }

    protected void twoLines(IHiddenRegionFormatter hrf) {
        hrf.setNewLines(2);
    }

    protected void optionalNewLine(IHiddenRegionFormatter hrf) {
        hrf.setNewLines(0, 0, 1);
    }

    protected void format(XstsModel xstsModel, IFormattableDocument document) {
        xstsModel.getEnumDeclarations().forEach(document::format);
        xstsModel.getVariableDeclarations().forEach(document::format);

        document.format(xstsModel.getInit());
        document.format(xstsModel.getTran());
        document.format(xstsModel.getEnv());
        document.format(xstsModel.getProperty());
    }

    protected void format(EnumDeclaration enumDeclaration, IFormattableDocument document) {
        formatBracketedDeclaration(enumDeclaration, document);

        document.append(regionFor(enumDeclaration).keyword("enum"), this::oneSpace);

        enumDeclaration.getLiterals().forEach(document::format);
    }

    protected void format(EnumLiteral literal, IFormattableDocument document) {
        document.prepend(regionFor(literal).feature(XstsPackage.Literals.ENUM_LITERAL__NAME), this::optionalNewLine);
        document.append(regionFor(literal).feature(XstsPackage.Literals.ENUM_LITERAL__NAME), this::noSpace);
    }

    protected void format(Property propertyDeclaration, IFormattableDocument document) {
        formatBracketedDeclaration(propertyDeclaration, document);

        document.append(regionFor(propertyDeclaration).keyword("prop"), this::oneSpace);

        document.format(propertyDeclaration.getInvariant());
    }

    protected void format(VariableDeclaration variableDeclaration, IFormattableDocument document) {
        formatSemicolonedLine(variableDeclaration, document);

        document.append(regionFor(variableDeclaration).keyword("ctrl"), this::oneSpace);
        document.append(regionFor(variableDeclaration).keyword("var"), this::oneSpace);
        document.prepend(regionFor(variableDeclaration).keyword(":"), this::noSpace);
        document.append(regionFor(variableDeclaration).keyword(":"), this::oneSpace);
        document.surround(regionFor(variableDeclaration).keyword("="), this::oneSpace);

        document.format(variableDeclaration.getType());
        document.format(variableDeclaration.getExpression());
    }

    protected void format(Transition transition, IFormattableDocument document) {
        formatSemicolonedLine(transition, document);

        document.append(regionFor(transition).keyword("trans"), this::oneSpace);
        document.append(regionFor(transition).keyword("init"), this::oneSpace);
        document.append(regionFor(transition).keyword("env"), this::oneSpace);

        transition.getBranches().forEach(document::format);
    }

    protected void format(HavocOperation havocOperation, IFormattableDocument document) {
        formatSemicolonedLine(havocOperation, document);

        document.append(regionFor(havocOperation).keyword("havoc"), this::oneSpace);

        document.format(havocOperation.getReference());
    }

    protected void format(AssumptionOperation assumptionOperation, IFormattableDocument document) {
        formatSemicolonedLine(assumptionOperation, document);

        document.append(regionFor(assumptionOperation).keyword("assume"), this::oneSpace);

        document.format(assumptionOperation.getExpression());
    }

    protected void format(AssignmentOperation assignmentOperation, IFormattableDocument document) {
        formatSemicolonedLine(assignmentOperation, document);

        document.surround(regionFor(assignmentOperation).keyword(":="), this::oneSpace);

        document.format(assignmentOperation.getReference());
        document.format(assignmentOperation.getExpression());
    }

    protected void format(SequenceOperation sequenceOperation, IFormattableDocument document) {
        formatBrackets(sequenceOperation, document);

        sequenceOperation.getSteps().forEach(document::format);
    }

    protected void format(ChoiceOperation choiceOperation, IFormattableDocument document) {
        formatSemicolonedLine(choiceOperation, document);

        document.append(regionFor(choiceOperation).keyword("choice"), this::oneSpace);
        document.surround(regionFor(choiceOperation).keyword("or"), this::oneSpace);
        document.surround(regionFor(choiceOperation).keyword("else"), this::oneSpace);

        choiceOperation.getBranches().forEach(document::format);
    }

    protected void format(IfOperation ifOperation, IFormattableDocument document) {
        formatBracketedDeclaration(ifOperation, document);

        document.append(regionFor(ifOperation).keyword("if"), this::oneSpace);

        document.format(ifOperation.getGuard());
        document.format(ifOperation.getBody());
        document.format(ifOperation.getElse());
    }

    protected void format(ForOperation forOperation, IFormattableDocument document) {
        formatSemicolonedLine(forOperation, document);

        document.append(regionFor(forOperation).keyword("for"), this::oneSpace);
        document.surround(regionFor(forOperation).keyword("from"), this::oneSpace);
        document.surround(regionFor(forOperation).keyword("to"), this::oneSpace);
        document.surround(regionFor(forOperation).keyword("do"), this::oneSpace);

        document.format(forOperation.getFrom());
        document.format(forOperation.getTo());
        document.format(forOperation.getLoopVar());
        document.format(forOperation.getBody());
    }

    protected void format(LocalVarDeclOperation localVarDeclarationOperation, IFormattableDocument document) {
        formatSemicolonedLine(localVarDeclarationOperation, document);

        document.append(regionFor(localVarDeclarationOperation).keyword("var"), this::oneSpace);
        document.append(regionFor(localVarDeclarationOperation).keyword(":"), this::oneSpace);
        document.surround(regionFor(localVarDeclarationOperation).keyword("="), this::oneSpace);

        document.format(localVarDeclarationOperation.getType());
        document.format(localVarDeclarationOperation.getExpression());
    }

    protected void format(ComparisonOperator comparisonOperator, IFormattableDocument document) {
        allRegionsFor(comparisonOperator).keywords(
                "<", "<=", ">", "==", "!="
        ).forEach(r -> document.surround(r, this::oneSpace));

        document.format(comparisonOperator.getLeft());
        document.format(comparisonOperator.getRight());
    }

    protected void format(ArithmeticBinaryOperator arithmeticBinaryOperator, IFormattableDocument document) {
        allRegionsFor(arithmeticBinaryOperator).keywords(
                "+", "-", "*", "/"
        ).forEach(r -> document.surround(r, this::oneSpace));

        document.format(arithmeticBinaryOperator.getLeft());
        document.format(arithmeticBinaryOperator.getRight());
    }

    protected void format(BooleanOperator booleanOperator, IFormattableDocument document) {
        allRegionsFor(booleanOperator).keywords(
                "&&", "||", "^^"
        ).forEach(r -> document.surround(r, this::oneSpace));

        document.format(booleanOperator.getLeft());
        document.format(booleanOperator.getRight());
    }

    protected void format(ArithmeticUnaryOperator unaryOperator, IFormattableDocument document) {
        document.append(regionFor(unaryOperator).keyword("+"), this::noSpace);
        document.append(regionFor(unaryOperator).keyword("-"), this::noSpace);

        document.format(unaryOperator.getBody());
    }

    protected void format(NegationOperator negationOperator, IFormattableDocument document) {
        document.append(regionFor(negationOperator).keyword("!"), this::noSpace);

        document.format(negationOperator.getBody());
    }

    protected void format(ConcreteLiteralArray arrayLiteral, IFormattableDocument document) {
        document.surround(regionFor(arrayLiteral).keyword("["), this::noSpace);
        document.surround(regionFor(arrayLiteral).keyword("]"), this::noSpace);

        arrayLiteral.getValues().forEach(document::format);

        document.format(arrayLiteral.getElseExpression());
        document.format(arrayLiteral.getIndexType());
    }

    protected void format(DefaultLiteralArray arrayLiteral, IFormattableDocument document) {
        document.surround(regionFor(arrayLiteral).keyword("["), this::noSpace);
        document.surround(regionFor(arrayLiteral).keyword("]"), this::noSpace);
        document.surround(regionFor(arrayLiteral).keyword("<-"), this::oneSpace);
        document.surround(regionFor(arrayLiteral).keyword("default"), this::oneSpace);

        document.format(arrayLiteral.getElseExpression());
        document.format(arrayLiteral.getIndexType());
    }

}
