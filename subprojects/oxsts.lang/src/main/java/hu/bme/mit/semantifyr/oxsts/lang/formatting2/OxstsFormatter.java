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

    protected void formatDeclaration(EObject declaration, IFormattableDocument document) {
        document.set(previousHiddenRegion(declaration), this::twoLines);
        formatSemicolonedLine(declaration, document);
        formatCurlyBrackets(declaration, document);
        formatCallBrackets(declaration, document);
    }

    protected void formatOperation(Operation operation, IFormattableDocument document) {
        document.set(previousHiddenRegion(operation), this::newLines);
        formatSemicolonedLine(operation, document);
        formatCurlyBrackets(operation, document);
        formatCallBrackets(operation, document);
    }

    protected void formatSemicolonedLine(EObject declaration, IFormattableDocument document) {
        document.prepend(regionFor(declaration).keyword(";"), this::noSpace);
    }

    protected void formatCurlyBrackets(EObject eObject, IFormattableDocument document) {
        document.prepend(regionFor(eObject).keyword("{"), this::oneSpace);
        document.prepend(regionFor(eObject).keyword("{"), this::noNewLine);
        document.prepend(regionFor(eObject).keyword("}"), this::newLine);
        document.interior(regionFor(eObject).keyword("{"), regionFor(eObject).keyword("}"), this::indent);
    }

    protected void formatCallBrackets(EObject eObject, IFormattableDocument document) {
        document.append(regionFor(eObject).keyword("("), this::noSpace);
        document.prepend(regionFor(eObject).keyword(")"), this::noSpace);
    }

    protected void newLines(IHiddenRegionFormatter hrf) {
        hrf.setNewLines(1, 1, 2);
    }

    protected void noNewLine(IHiddenRegionFormatter hrf) {
        hrf.setNewLines(0);
    }

    protected void twoLines(IHiddenRegionFormatter hrf) {
        hrf.setNewLines(1, 2, 2);
    }

    protected void optionalNewLine(IHiddenRegionFormatter hrf) {
        hrf.setNewLines(0, 0, 1);
    }

    protected void format(OxstsModelPackage _package, IFormattableDocument document) {
        _package.getImports().forEach(document::format);
        _package.getDeclarations().forEach(document::format);
    }

    protected void format(InlinedOxsts inlinedOxsts, IFormattableDocument document) {
        inlinedOxsts.getVariables().forEach(document::format);

        document.format(inlinedOxsts.getRootFeature());
        document.format(inlinedOxsts.getInitTransition());
        document.format(inlinedOxsts.getMainTransition());
        document.format(inlinedOxsts.getProperty());
    }

    protected void format(ImportStatement _import, IFormattableDocument document) {
        formatDeclaration(_import, document);

        document.append(regionFor(_import).keyword("import"), this::oneSpace);
        document.surround(regionFor(_import).keyword("as"), this::oneSpace);
    }

    protected void format(Annotation annotation, IFormattableDocument document) {
        formatCallBrackets(annotation, document);
        formatSemicolonedLine(annotation, document);

        document.append(regionFor(annotation).keyword("@"), this::noSpace);
        document.set(nextHiddenRegion(annotation), this::newLine);

        annotation.getArguments().forEach(document::format);
    }

    protected void format(AnnotationContainer annotationContainer, IFormattableDocument document) {
        annotationContainer.getAnnotations().forEach(document::format);
    }

    protected void format(DataTypeDeclaration dataTypeDeclaration, IFormattableDocument document) {
        formatDeclaration(dataTypeDeclaration, document);

        document.append(regionFor(dataTypeDeclaration).keyword("extern"), this::oneSpace);
        document.append(regionFor(dataTypeDeclaration).keyword("datatype"), this::oneSpace);

        document.format(dataTypeDeclaration.getAnnotation());
    }

    protected void format(EnumDeclaration enumDeclaration, IFormattableDocument document) {
        formatDeclaration(enumDeclaration, document);

        document.append(regionFor(enumDeclaration).keyword("enum"), this::oneSpace);

        document.format(enumDeclaration.getAnnotation());

        enumDeclaration.getLiterals().forEach(document::format);
    }

    protected void format(EnumLiteral literal, IFormattableDocument document) {
        document.prepend(regionFor(literal).feature(OxstsPackage.Literals.NAMED_ELEMENT__NAME), this::optionalNewLine);
        document.append(regionFor(literal).feature(OxstsPackage.Literals.NAMED_ELEMENT__NAME), this::noSpace);
    }

    protected void format(RecordDeclaration recordDeclaration, IFormattableDocument document) {
        formatDeclaration(recordDeclaration, document);

        document.append(regionFor(recordDeclaration).keyword("record"), this::oneSpace);

        document.format(recordDeclaration.getAnnotation());

        recordDeclaration.getMembers().forEach(document::format);
    }

    protected void format(ClassDeclaration classDeclaration, IFormattableDocument document) {
        formatDeclaration(classDeclaration, document);

        document.append(regionFor(classDeclaration).keyword("abstract"), this::oneSpace);
        document.append(regionFor(classDeclaration).keyword("class"), this::oneSpace);
        document.surround(regionFor(classDeclaration).keyword(":"), this::oneSpace);

        document.prepend(allRegionsFor(classDeclaration).keyword(","), this::noSpace);
        document.append(allRegionsFor(classDeclaration).keyword(","), this::oneSpace);

        document.format(classDeclaration.getAnnotation());

        classDeclaration.getMembers().forEach(document::format);
    }

    protected void format(AnnotationDeclaration annotationDeclaration, IFormattableDocument document) {
        formatDeclaration(annotationDeclaration, document);

        document.append(regionFor(annotationDeclaration).keyword("annotation"), this::oneSpace);

        annotationDeclaration.getParameters().forEach(document::format);
    }

    protected void format(VariableDeclaration variableDeclaration, IFormattableDocument document) {
        formatDeclaration(variableDeclaration, document);

        document.append(regionFor(variableDeclaration).keyword("var"), this::oneSpace);
        document.prepend(regionFor(variableDeclaration).keyword(":"), this::noSpace);
        document.append(regionFor(variableDeclaration).keyword(":"), this::oneSpace);
        if (variableDeclaration.getMultiplicity() != null) {
            document.set(previousHiddenRegion(variableDeclaration.getMultiplicity()), this::noSpace);
        }
        document.surround(regionFor(variableDeclaration).keyword(":="), this::oneSpace);

        document.format(variableDeclaration.getMultiplicity());
        document.format(variableDeclaration.getExpression());
        document.format(variableDeclaration.getAnnotation());
    }

    protected void format(PropertyDeclaration propertyDeclaration, IFormattableDocument document) {
        formatDeclaration(propertyDeclaration, document);

        document.append(regionFor(propertyDeclaration).keyword("redefine"), this::oneSpace);
        document.append(regionFor(propertyDeclaration).keyword("prop"), this::oneSpace);
        document.append(regionFor(propertyDeclaration).keyword("return"), this::oneSpace);
        document.prepend(regionFor(propertyDeclaration).keyword(":"), this::noSpace);
        document.append(regionFor(propertyDeclaration).keyword(":"), this::oneSpace);
        document.append(regionFor(propertyDeclaration).keyword("{"), this::newLine);

        document.format(propertyDeclaration.getExpression());
        document.format(propertyDeclaration.getAnnotation());

        propertyDeclaration.getParameters().forEach(document::format);
    }

    protected void format(TransitionDeclaration transitionDeclaration, IFormattableDocument document) {
        formatDeclaration(transitionDeclaration, document);

        document.append(regionFor(transitionDeclaration).keyword("redefine"), this::oneSpace);
        document.append(regionFor(transitionDeclaration).keyword("tran"), this::oneSpace);
        document.append(regionFor(transitionDeclaration).keyword("init"), this::oneSpace);
        document.append(regionFor(transitionDeclaration).keyword("env"), this::oneSpace);
        document.append(regionFor(transitionDeclaration).keyword("havoc"), this::oneSpace);

        document.format(transitionDeclaration.getAnnotation());

        transitionDeclaration.getBranches().forEach(document::format);
    }

    protected void format(FeatureDeclaration featureDeclaration, IFormattableDocument document) {
        formatDeclaration(featureDeclaration, document);

        document.append(regionFor(featureDeclaration).keyword("redefine"), this::oneSpace);
        document.append(regionFor(featureDeclaration).keyword("refers"), this::oneSpace);
        document.append(regionFor(featureDeclaration).keyword("contains"), this::oneSpace);
        document.append(regionFor(featureDeclaration).keyword("container"), this::oneSpace);
        document.append(regionFor(featureDeclaration).keyword("derived"), this::oneSpace);
        document.append(regionFor(featureDeclaration).keyword("features"), this::oneSpace);
        document.append(regionFor(featureDeclaration).keyword("global"), this::oneSpace);
        document.append(regionFor(featureDeclaration).keyword("feature"), this::oneSpace);
        document.append(regionFor(featureDeclaration).keyword("containment"), this::oneSpace);
        document.append(regionFor(featureDeclaration).keyword("reference"), this::oneSpace);

        document.prepend(regionFor(featureDeclaration).keyword(":"), this::noSpace);
        document.append(regionFor(featureDeclaration).keyword(":"), this::oneSpace);
        document.surround(regionFor(featureDeclaration).keyword("="), this::oneSpace);

        if (featureDeclaration.getMultiplicity() != null) {
            document.set(previousHiddenRegion(featureDeclaration.getMultiplicity()), this::noSpace);
        }
        document.prepend(allRegionsFor(featureDeclaration).keyword(","), this::noSpace);
        document.append(allRegionsFor(featureDeclaration).keyword(","), this::oneSpace);

        document.format(featureDeclaration.getMultiplicity());
        document.format(featureDeclaration.getExpression());
        document.format(featureDeclaration.getAnnotation());

        featureDeclaration.getInnerFeatures().forEach(document::format);
    }

    protected void format(HavocOperation havocOperation, IFormattableDocument document) {
        formatOperation(havocOperation, document);

        document.append(regionFor(havocOperation).keyword("havoc"), this::oneSpace);

        document.format(havocOperation.getReference());
    }

    protected void format(AssumptionOperation assumptionOperation, IFormattableDocument document) {
        formatOperation(assumptionOperation, document);

        document.append(regionFor(assumptionOperation).keyword("assume"), this::oneSpace);

        document.format(assumptionOperation.getExpression());
    }

    protected void format(AssignmentOperation assignmentOperation, IFormattableDocument document) {
        formatOperation(assignmentOperation, document);

        document.surround(regionFor(assignmentOperation).keyword(":="), this::oneSpace);

        document.format(assignmentOperation.getReference());
        document.format(assignmentOperation.getExpression());
    }

    protected void format(SequenceOperation sequenceOperation, IFormattableDocument document) {
        // do not add new line before sequences
        formatSemicolonedLine(sequenceOperation, document);
        formatCurlyBrackets(sequenceOperation, document);

        sequenceOperation.getSteps().forEach(document::format);
    }

    protected void format(ChoiceOperation choiceOperation, IFormattableDocument document) {
        formatOperation(choiceOperation, document);

        document.append(regionFor(choiceOperation).keyword("choice"), this::oneSpace);
        document.surround(regionFor(choiceOperation).keyword("or"), this::oneSpace);
        document.surround(regionFor(choiceOperation).keyword("else"), this::oneSpace);

        choiceOperation.getBranches().forEach(document::format);
    }

    protected void format(IfOperation ifOperation, IFormattableDocument document) {
        formatOperation(ifOperation, document);

        document.append(regionFor(ifOperation).keyword("if"), this::oneSpace);

        document.format(ifOperation.getGuard());
        document.format(ifOperation.getBody());
        document.format(ifOperation.getElse());
    }

    protected void format(ForOperation forOperation, IFormattableDocument document) {
        formatOperation(forOperation, document);

        document.set(previousHiddenRegion(forOperation.getLoopVariable()), this::noSpace);
        document.append(regionFor(forOperation).keyword("for"), this::oneSpace);
        document.surround(regionFor(forOperation).keyword("in"), this::oneSpace);

        document.format(forOperation.getRangeExpression());
        document.format(forOperation.getBody());
    }

    protected void format(LocalVarDeclarationOperation localVarDeclarationOperation, IFormattableDocument document) {
        formatOperation(localVarDeclarationOperation, document);

        document.append(regionFor(localVarDeclarationOperation).keyword("var"), this::oneSpace);
        document.append(regionFor(localVarDeclarationOperation).keyword(":"), this::oneSpace);
        document.surround(regionFor(localVarDeclarationOperation).keyword(":="), this::oneSpace);

        document.format(localVarDeclarationOperation.getMultiplicity());
        document.format(localVarDeclarationOperation.getExpression());
        document.format(localVarDeclarationOperation.getAnnotation());
    }

    protected void format(InlineCall inlineCall, IFormattableDocument document) {
        formatOperation(inlineCall, document);

        document.append(regionFor(inlineCall).keyword("inline"), this::oneSpace);

        document.format(inlineCall.getCallExpression());
    }

    protected void format(InlineSeqFor inlineFor, IFormattableDocument document) {
        formatOperation(inlineFor, document);

        document.surround(regionFor(inlineFor).keyword("inline"), this::oneSpace);
        document.surround(regionFor(inlineFor).keyword("for"), this::oneSpace);
        document.surround(regionFor(inlineFor).keyword("seq"), this::oneSpace);

        document.surround(regionFor(inlineFor).keyword("in"), this::oneSpace);

//        document.format(inlineFor.getLoopVariable());
        document.format(inlineFor.getRangeExpression());
        document.format(inlineFor.getBody());
        document.format(inlineFor.getElse());
    }

    protected void format(InlineChoiceFor inlineFor, IFormattableDocument document) {
        formatOperation(inlineFor, document);

        document.surround(regionFor(inlineFor).keyword("inline"), this::oneSpace);
        document.surround(regionFor(inlineFor).keyword("for"), this::oneSpace);
        document.surround(regionFor(inlineFor).keyword("choice"), this::oneSpace);
        document.surround(regionFor(inlineFor).keyword("else"), this::oneSpace);

        document.surround(regionFor(inlineFor).keyword("in"), this::oneSpace);

//        document.format(inlineFor.getLoopVariable());
        document.format(inlineFor.getRangeExpression());
        document.format(inlineFor.getBody());
        document.format(inlineFor.getElse());
    }

    protected void format(InlineIfOperation inlineIfOperation, IFormattableDocument document) {
        formatOperation(inlineIfOperation, document);

        document.surround(regionFor(inlineIfOperation).keyword("inline"), this::oneSpace);
        document.surround(regionFor(inlineIfOperation).keyword("if"), this::oneSpace);

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
        document.prepend(regionFor(parameterDeclaration).keyword(":"), this::noSpace);
        document.append(regionFor(parameterDeclaration).keyword(":"), this::oneSpace);
        if (parameterDeclaration.getMultiplicity() != null) {
            document.set(previousHiddenRegion(parameterDeclaration.getMultiplicity()), this::noSpace);
        }
        document.set(nextHiddenRegion(parameterDeclaration), this::noSpace);

        document.format(parameterDeclaration.getMultiplicity());
    }

    protected void format(Argument argument, IFormattableDocument document) {
        document.set(previousHiddenRegion(argument), this::optionalNewLine);
        document.surround(regionFor(argument).keyword("="), this::oneSpace);
        document.set(nextHiddenRegion(argument), this::noSpace);

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

    protected void format(ArithmeticUnaryOperator unaryOperator, IFormattableDocument document) {
        document.set(previousHiddenRegion(unaryOperator.getBody()), this::noSpace);

        document.format(unaryOperator.getBody());
    }

    protected void format(NegationOperator unaryOperator, IFormattableDocument document) {
        document.set(previousHiddenRegion(unaryOperator.getBody()), this::noSpace);

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
        document.prepend(regionFor(postfixUnaryExpression).keyword("("), this::noSpace);
        formatCallBrackets(postfixUnaryExpression, document);

        document.format(postfixUnaryExpression.getPrimary());

        postfixUnaryExpression.getArguments().forEach(document::format);
    }

    protected void format(IndexingSuffixExpression postfixUnaryExpression, IFormattableDocument document) {
        document.surround(regionFor(postfixUnaryExpression).keyword("["), this::noSpace);
        document.surround(regionFor(postfixUnaryExpression).keyword("]"), this::noSpace);

        document.format(postfixUnaryExpression.getPrimary());
        document.format(postfixUnaryExpression.getIndex());
    }

}
