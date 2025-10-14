/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.formatting2;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.formatting2.AbstractJavaFormatter;
import org.eclipse.xtext.formatting2.IFormattableDocument;
import org.eclipse.xtext.formatting2.IHiddenRegionFormatter;

@SuppressWarnings("UnstableApiUsage")
public class OxstsFormatter extends AbstractJavaFormatter {

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
        document.append(regionFor(eObject).keyword("{"), this::newLine);
        document.prepend(regionFor(eObject).keyword("}"), this::newLine);
        document.interior(regionFor(eObject).keyword("{"), regionFor(eObject).keyword("}"), this::indent);
    }
    protected void formatCallLike(EObject eObject, IFormattableDocument document) {
        document.append(regionFor(eObject).keyword("("), this::noSpace);
        document.surround(regionFor(eObject).keyword("("), this::optionalNewLine);
        document.prepend(regionFor(eObject).keyword(")"), this::noSpace);
        document.surround(regionFor(eObject).keyword(")"), this::optionalNewLine);

        document.interior(regionFor(eObject).keyword("("), regionFor(eObject).keyword(")"), this::indent);
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

    protected void format(OxstsModelPackage _package, IFormattableDocument document) {
        document.append(regionFor(_package).feature(OxstsPackage.Literals.NAMED_ELEMENT__NAME), this::twoLines);

        _package.getImports().forEach(document::format);
        _package.getDeclarations().forEach(document::format);
    }

    protected void format(InlinedOxsts inlinedOxsts, IFormattableDocument document) {
        document.append(regionFor(inlinedOxsts).feature(OxstsPackage.Literals.INLINED_OXSTS__CLASS_DECLARATION), this::twoLines);

        document.prepend(regionFor(inlinedOxsts.getRootFeature()).feature(OxstsPackage.Literals.FEATURE_DECLARATION__KIND), this::twoLines);
        document.prepend(regionFor(inlinedOxsts.getInitTransition()).feature(OxstsPackage.Literals.TRANSITION_DECLARATION__KIND), this::twoLines);
        document.prepend(regionFor(inlinedOxsts.getMainTransition()).feature(OxstsPackage.Literals.TRANSITION_DECLARATION__KIND), this::twoLines);
        document.prepend(regionFor(inlinedOxsts.getProperty()).keyword("prop"), this::twoLines);

        inlinedOxsts.getVariables().forEach(document::format);

        document.format(inlinedOxsts.getRootFeature());
        document.format(inlinedOxsts.getInitTransition());
        document.format(inlinedOxsts.getMainTransition());
        document.format(inlinedOxsts.getProperty());
    }

    protected void format(ImportStatement _import, IFormattableDocument document) {
        formatSemicolonedLine(_import, document);

        document.append(regionFor(_import).keyword("import"), this::oneSpace);
        document.append(regionFor(_import).feature(OxstsPackage.Literals.IMPORT_STATEMENT__IMPORTED_PACKAGE), this::newLines);
    }

    protected void format(Annotation annotation, IFormattableDocument document) {
        formatSemicolonedLine(annotation, document);

        document.prepend(regionFor(annotation).keyword("@"), this::newLines);
        document.append(regionFor(annotation).keyword("@"), this::noSpace);

        formatCallLike(annotation, document);

        annotation.getArguments().forEach(document::format);
    }

    protected void format(AnnotationContainer annotationContainer, IFormattableDocument document) {
        annotationContainer.getAnnotations().forEach(document::format);
    }

    protected void format(DataTypeDeclaration dataTypeDeclaration, IFormattableDocument document) {
        formatSemicolonedLine(dataTypeDeclaration, document);

        document.append(regionFor(dataTypeDeclaration).keyword("extern"), this::oneSpace);
        document.append(regionFor(dataTypeDeclaration).keyword("datatype"), this::oneSpace);

        document.format(dataTypeDeclaration.getAnnotation());
    }

    protected void format(EnumDeclaration enumDeclaration, IFormattableDocument document) {
        formatBracketedDeclaration(enumDeclaration, document);

        document.append(regionFor(enumDeclaration).keyword("enum"), this::oneSpace);

        document.format(enumDeclaration.getAnnotation());

        enumDeclaration.getLiterals().forEach(document::format);
    }

    protected void format(EnumLiteral literal, IFormattableDocument document) {
        document.prepend(regionFor(literal).feature(OxstsPackage.Literals.NAMED_ELEMENT__NAME), this::optionalNewLine);
        document.append(regionFor(literal).feature(OxstsPackage.Literals.NAMED_ELEMENT__NAME), this::noSpace);
    }

    protected void format(RecordDeclaration recordDeclaration, IFormattableDocument document) {
        formatBracketedDeclaration(recordDeclaration, document);

        document.append(regionFor(recordDeclaration).keyword("record"), this::oneSpace);

        document.format(recordDeclaration.getAnnotation());

        recordDeclaration.getMembers().forEach(document::format);
    }

    protected void format(ClassDeclaration classDeclaration, IFormattableDocument document) {
        formatBracketedDeclaration(classDeclaration, document);

        document.append(regionFor(classDeclaration).keyword("abstract"), this::oneSpace);
        document.append(regionFor(classDeclaration).keyword("class"), this::oneSpace);
        document.surround(regionFor(classDeclaration).keyword(":"), this::oneSpace);

        document.prepend(allRegionsFor(classDeclaration).keyword(","), this::noSpace);
        document.append(allRegionsFor(classDeclaration).keyword(","), this::oneSpace);

        document.format(classDeclaration.getAnnotation());

        classDeclaration.getMembers().forEach(document::format);
    }

    protected void format(AnnotationDeclaration annotationDeclaration, IFormattableDocument document) {
        formatSemicolonedLine(annotationDeclaration, document);

        document.append(regionFor(annotationDeclaration).keyword("annotation"), this::oneSpace);

        formatCallLike(annotationDeclaration, document);

        annotationDeclaration.getParameters().forEach(document::format);
    }

    protected void format(VariableDeclaration variableDeclaration, IFormattableDocument document) {
        formatSemicolonedLine(variableDeclaration, document);

        document.append(regionFor(variableDeclaration).keyword("ctrl"), this::oneSpace);
        document.append(regionFor(variableDeclaration).keyword("var"), this::oneSpace);
        document.prepend(regionFor(variableDeclaration).keyword(":"), this::noSpace);
        document.append(regionFor(variableDeclaration).keyword(":"), this::oneSpace);
        document.surround(regionFor(variableDeclaration).keyword(":="), this::oneSpace);

        document.format(variableDeclaration.getMultiplicity());
        document.format(variableDeclaration.getExpression());
        document.format(variableDeclaration.getAnnotation());
    }

    protected void format(PropertyDeclaration propertyDeclaration, IFormattableDocument document) {
        formatBracketedDeclaration(propertyDeclaration, document);

        document.append(regionFor(propertyDeclaration).keyword("redefine"), this::oneSpace);
        document.append(regionFor(propertyDeclaration).keyword("prop"), this::oneSpace);
        document.append(regionFor(propertyDeclaration).keyword("return"), this::oneSpace);
        document.prepend(regionFor(propertyDeclaration).keyword(":"), this::noSpace);
        document.append(regionFor(propertyDeclaration).keyword(":"), this::oneSpace);

        document.format(propertyDeclaration.getExpression());
        document.format(propertyDeclaration.getAnnotation());

        propertyDeclaration.getParameters().forEach(document::format);
    }

    protected void format(TransitionDeclaration transitionDeclaration, IFormattableDocument document) {
        formatSemicolonedLine(transitionDeclaration, document);

        document.append(regionFor(transitionDeclaration).keyword("redefine"), this::oneSpace);
        document.append(regionFor(transitionDeclaration).keyword("tran"), this::oneSpace);
        document.append(regionFor(transitionDeclaration).keyword("init"), this::oneSpace);
        document.append(regionFor(transitionDeclaration).keyword("env"), this::oneSpace);
        document.append(regionFor(transitionDeclaration).keyword("havoc"), this::oneSpace);
        document.format(transitionDeclaration.getAnnotation());

        transitionDeclaration.getBranches().forEach(document::format);
    }

    protected void format(FeatureDeclaration featureDeclaration, IFormattableDocument document) {
        formatBracketedDeclaration(featureDeclaration, document);

        document.append(regionFor(featureDeclaration).keyword("redefine"), this::oneSpace);
        document.append(regionFor(featureDeclaration).keyword("refers"), this::oneSpace);
        document.append(regionFor(featureDeclaration).keyword("contains"), this::oneSpace);
        document.append(regionFor(featureDeclaration).keyword("container"), this::oneSpace);
        document.append(regionFor(featureDeclaration).keyword("derived"), this::oneSpace);
        document.append(regionFor(featureDeclaration).keyword("features"), this::oneSpace);
        document.prepend(regionFor(featureDeclaration).keyword(":"), this::noSpace);
        document.append(regionFor(featureDeclaration).keyword(":"), this::oneSpace);
        document.surround(regionFor(featureDeclaration).keyword("="), this::oneSpace);

        featureDeclaration.getInnerFeatures().forEach(document::format);
        document.format(featureDeclaration.getMultiplicity());
        document.format(featureDeclaration.getExpression());
        document.format(featureDeclaration.getAnnotation());
    }

    protected void format(HavocOperation havocOperation, IFormattableDocument document) {
        formatSemicolonedLine(havocOperation, document);

        document.append(regionFor(havocOperation).keyword("havoc"), this::oneSpace);

        formatCallLike(havocOperation, document);

        document.format(havocOperation.getReference());
    }

    protected void format(AssumptionOperation assumptionOperation, IFormattableDocument document) {
        formatSemicolonedLine(assumptionOperation, document);

        document.append(regionFor(assumptionOperation).keyword("assume"), this::oneSpace);

        formatCallLike(assumptionOperation, document);

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

        formatCallLike(choiceOperation, document);

        choiceOperation.getBranches().forEach(document::format);
    }

    protected void format(IfOperation ifOperation, IFormattableDocument document) {
        formatBracketedDeclaration(ifOperation, document);

        document.append(regionFor(ifOperation).keyword("if"), this::oneSpace);

        formatCallLike(ifOperation, document);

        document.format(ifOperation.getGuard());
        document.format(ifOperation.getBody());
        document.format(ifOperation.getElse());
    }

    protected void format(ForOperation forOperation, IFormattableDocument document) {
        formatSemicolonedLine(forOperation, document);

        document.append(regionFor(forOperation).keyword("for"), this::oneSpace);
        document.surround(regionFor(forOperation).keyword("in"), this::oneSpace);

        formatCallLike(forOperation, document);

//        document.format(forOperation.getLoopVariable());
        document.format(forOperation.getRangeExpression());
        document.format(forOperation.getBody());
    }

    protected void format(LocalVarDeclarationOperation localVarDeclarationOperation, IFormattableDocument document) {
        formatSemicolonedLine(localVarDeclarationOperation, document);

        document.append(regionFor(localVarDeclarationOperation).keyword("var"), this::oneSpace);
        document.append(regionFor(localVarDeclarationOperation).keyword(":"), this::oneSpace);
        document.surround(regionFor(localVarDeclarationOperation).keyword(":="), this::oneSpace);

        document.format(localVarDeclarationOperation.getMultiplicity());
        document.format(localVarDeclarationOperation.getExpression());
        document.format(localVarDeclarationOperation.getAnnotation());
    }

    protected void format(InlineCall inlineCall, IFormattableDocument document) {
        formatSemicolonedLine(inlineCall, document);

        document.surround(regionFor(inlineCall).keyword("inline"), this::oneSpace);

        formatCallLike(inlineCall, document);

        document.format(inlineCall.getCallExpression());
    }

    protected void format(InlineSeqFor inlineFor, IFormattableDocument document) {
        formatSemicolonedLine(inlineFor, document);

        document.surround(regionFor(inlineFor).keyword("inline"), this::oneSpace);
        document.surround(regionFor(inlineFor).keyword("for"), this::oneSpace);
        document.surround(regionFor(inlineFor).keyword("seq"), this::oneSpace);

        document.surround(regionFor(inlineFor).keyword("in"), this::oneSpace);

        formatCallLike(inlineFor, document);

//        document.format(inlineFor.getLoopVariable());
        document.format(inlineFor.getRangeExpression());
        document.format(inlineFor.getBody());
        document.format(inlineFor.getElse());
    }

    protected void format(InlineChoiceFor inlineFor, IFormattableDocument document) {
        formatSemicolonedLine(inlineFor, document);

        document.surround(regionFor(inlineFor).keyword("inline"), this::oneSpace);
        document.surround(regionFor(inlineFor).keyword("for"), this::oneSpace);
        document.surround(regionFor(inlineFor).keyword("choice"), this::oneSpace);
        document.surround(regionFor(inlineFor).keyword("else"), this::oneSpace);

        document.surround(regionFor(inlineFor).keyword("in"), this::oneSpace);

        formatCallLike(inlineFor, document);

//        document.format(inlineFor.getLoopVariable());
        document.format(inlineFor.getRangeExpression());
        document.format(inlineFor.getBody());
        document.format(inlineFor.getElse());
    }

    protected void format(InlineIfOperation inlineIfOperation, IFormattableDocument document) {
        formatSemicolonedLine(inlineIfOperation, document);

        document.surround(regionFor(inlineIfOperation).keyword("inline"), this::oneSpace);
        document.surround(regionFor(inlineIfOperation).keyword("if"), this::oneSpace);

        formatCallLike(inlineIfOperation, document);

        document.format(inlineIfOperation.getGuard());
        document.format(inlineIfOperation.getBody());
        document.format(inlineIfOperation.getElse());
    }

    protected void format(Multiplicity multiplicity, IFormattableDocument document) {
        document.surround(regionFor(multiplicity).keyword("["), this::noSpace);
        document.prepend(regionFor(multiplicity).keyword("]"), this::noSpace);

        if (multiplicity instanceof DefiniteMultiplicity definiteMultiplicity) {
            document.format(definiteMultiplicity.getExpression());
        }
    }

    protected void format(ParameterDeclaration parameterDeclaration, IFormattableDocument document) {
        document.set(previousHiddenRegion(parameterDeclaration), this::optionalNewLine);
        document.set(nextHiddenRegion(parameterDeclaration), this::noSpace);
        document.surround(regionFor(parameterDeclaration).keyword("="), this::oneSpace);

        document.format(parameterDeclaration.getMultiplicity());
    }

    protected void format(Argument argument, IFormattableDocument document) {
        document.set(previousHiddenRegion(argument), this::optionalNewLine);
        document.set(nextHiddenRegion(argument), this::noSpace);
        document.surround(regionFor(argument).keyword("="), this::oneSpace);

        document.format(argument.getExpression());
    }

    protected void format(RangeExpression rangeExpression, IFormattableDocument document) {
        document.surround(regionFor(rangeExpression).keyword(".."), this::noSpace);
        document.surround(regionFor(rangeExpression).keyword("..<"), this::noSpace);

        document.format(rangeExpression.getLeft());
        document.format(rangeExpression.getRight());
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

    protected void format(UnaryOperator unaryOperator, IFormattableDocument document) {
        document.append(regionFor(unaryOperator).keyword("+"), this::noSpace);
        document.append(regionFor(unaryOperator).keyword("-"), this::noSpace);
        document.append(regionFor(unaryOperator).keyword("!"), this::noSpace);

        document.format(unaryOperator.getBody());
    }

    protected void format(ArrayLiteral arrayLiteral, IFormattableDocument document) {
        document.surround(regionFor(arrayLiteral).keyword("["), this::noSpace);
        document.surround(regionFor(arrayLiteral).keyword("]"), this::noSpace);

        arrayLiteral.getValues().forEach(document::format);
    }

    protected void format(SelfReference selfReference, IFormattableDocument document) {
        document.surround(regionFor(selfReference).keyword("@"), this::noSpace);
    }

    protected void format(NavigationSuffixExpression postfixUnaryExpression, IFormattableDocument document) {
        document.surround(regionFor(postfixUnaryExpression).keyword("."), this::noSpace);

        document.format(postfixUnaryExpression.getPrimary());
    }

    protected void format(CallSuffixExpression postfixUnaryExpression, IFormattableDocument document) {
        formatCallLike(postfixUnaryExpression, document);
        document.prepend(regionFor(postfixUnaryExpression).keyword("("), this::noSpace);

        document.format(postfixUnaryExpression.getPrimary());

        postfixUnaryExpression.getArguments().forEach(document::format);
    }

    protected void format(IndexingSuffixExpression postfixUnaryExpression, IFormattableDocument document) {
        document.surround(regionFor(postfixUnaryExpression).keyword("["), this::noSpace);
        document.prepend(regionFor(postfixUnaryExpression).keyword("]"), this::noSpace);

        document.format(postfixUnaryExpression.getPrimary());
        document.format(postfixUnaryExpression.getIndex());
    }

}
