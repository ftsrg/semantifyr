/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package hu.bme.mit.semantifyr.oxsts.lang.formatting2;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.formatting2.AbstractJavaFormatter;
import org.eclipse.xtext.formatting2.IFormattableDocument;

public class OxstsFormatter extends AbstractJavaFormatter {

    protected void format(hu.bme.mit.semantifyr.oxsts.model.oxsts.Package _package, IFormattableDocument document) {
        for (var _import : _package.getImports()) {
            document.format(_import);
        }

        for (var pattern : _package.getPatterns()) {
            document.format(pattern);
        }

        for (var _enum : _package.getEnums()) {
            document.format(_enum);
        }

        for (var type : _package.getTypes()) {
            document.format(type);
        }
    }

    protected void format(Import _import, IFormattableDocument document) {
        document.prepend(regionFor(_import).keyword("import"), (a) -> {
            a.setNewLines(1, 1, 2);
        });
    }

    protected void format(hu.bme.mit.semantifyr.oxsts.model.oxsts.Enum _enum, IFormattableDocument document) {
        document.prepend(regionFor(_enum).keyword("enum"), (a) -> {
            a.setNewLines(1, 1, 2);
        });
        document.prepend(regionFor(_enum).keyword(","), this::noSpace);
        document.append(regionFor(_enum).keyword(","), this::noSpace);
        setupBrackets(_enum, document);

        for (var literal : _enum.getLiterals()) {
            document.format(literal);
        }
    }

    protected void format(EnumLiteral literal, IFormattableDocument document) {
        document.prepend(regionFor(literal).feature(OxstsPackage.Literals.ELEMENT__NAME), this::newLine);
    }

    protected void format(Type type, IFormattableDocument document) {
        document.prepend(regionFor(type).keyword("type"), (a) -> {
            a.setNewLines(1, 1, 2);
        });
        document.prepend(regionFor(type).keyword("target"), (a) -> {
            a.setNewLines(1, 1, 2);
        });

        formatBaseType(type, document);

        for (var variable : type.getVariables()) {
            document.format(variable);
        }

        for (var property : type.getProperties()) {
            document.format(property);
        }
    }

    protected void format(Variable variable, IFormattableDocument document) {
        document.prepend(regionFor(variable).keyword("var"), this::newLine);
    }

    protected void format(Feature feature, IFormattableDocument document) {
        document.prepend(regionFor(feature).keyword("feature"), this::newLine);
        document.prepend(regionFor(feature).keyword("reference"), this::newLine);
        document.prepend(regionFor(feature).keyword("derived"), this::newLine);
    }

    protected void format(Containment containment, IFormattableDocument document) {
        document.prepend(regionFor(containment).keyword("containment"), this::newLine);

        formatBaseType(containment, document);
    }

    protected void formatBaseType(BaseType type, IFormattableDocument document) {
        setupBrackets(type, document);

        for (var feature : type.getFeatures()) {
            document.format(feature);
        }

        for (var init : type.getInitTransition()) {
            document.format(init);
        }

        for (var transition : type.getTransitions()) {
            document.format(transition);
        }

        for (var main : type.getMainTransition()) {
            document.format(main);
        }

        for (var havoc : type.getHavocTransition()) {
            document.format(havoc);
        }
    }

    protected void format(Transition transition, IFormattableDocument document) {
        document.prepend(regionFor(transition).keyword("tran"), this::newLine);
        document.prepend(regionFor(transition).keyword("havoc"), this::newLine);
        document.prepend(regionFor(transition).keyword("init"), this::newLine);
        setupBrackets(transition, document);

        for (var operation : transition.getOperation()) {
            document.format(operation);
        }
    }

    protected void format(CompositeOperation operation, IFormattableDocument document) {
        setupBrackets(operation, document);

        for (var inner : operation.getOperation()) {
            document.format(inner);
        }
    }

    protected void format(SequenceOperation operation, IFormattableDocument document) {
        format((CompositeOperation) operation, document);
    }

    protected void format(ChoiceOperation operation, IFormattableDocument document) {
        document.prepend(regionFor(operation).keyword("choice"), this::newLine);

        format((CompositeOperation) operation, document);

        if (operation.getElse() != null) {
            document.format(operation.getElse());
        }
    }

    protected void format(IfOperation operation, IFormattableDocument document) {
        document.prepend(regionFor(operation).keyword("if"), this::newLine);
        document.format(operation.getBody());

        if (operation.getElse() != null) {
            document.format(operation.getElse());
        }
    }

    protected void format(AssumptionOperation operation, IFormattableDocument document) {
        document.prepend(regionFor(operation).keyword("assume"), this::newLine);
        document.append(regionFor(operation).keyword("("), this::noSpace);
        document.prepend(regionFor(operation).keyword(")"), this::noSpace);

        document.format(operation.getExpression());
    }

    protected void format(HavocOperation operation, IFormattableDocument document) {
        document.prepend(regionFor(operation).keyword("havoc"), this::newLine);
        document.append(regionFor(operation).keyword("("), this::noSpace);
        document.prepend(regionFor(operation).keyword(")"), this::noSpace);

        document.format(operation.getReferenceExpression());
    }

    protected void format(AssignmentOperation operation, IFormattableDocument document) {
        document.prepend(regionFor(operation).feature(OxstsPackage.Literals.ASSIGNMENT_OPERATION__REFERENCE), this::newLine);

        document.format(operation.getReference());
        document.format(operation.getExpression());
    }

    protected void format(InlineCall operation, IFormattableDocument document) {
        if (operation.isStatic())  {
            document.prepend(regionFor(operation).keyword("static"), this::newLine);
        } else {
            document.prepend(regionFor(operation).keyword("inline"), this::newLine);
        }

        document.prepend(regionFor(operation).keyword("("), this::noSpace);
        document.append(regionFor(operation).keyword("("), this::noSpace);
        document.prepend(regionFor(operation).keyword(")"), this::noSpace);
    }

    protected void format(InlineChoice operation, IFormattableDocument document) {
        document.prepend(regionFor(operation).keyword("inline"), this::newLine);

        if (operation.getElse() != null) {
            document.format(operation.getElse());
        }
    }

    protected void format(InlineSeq operation, IFormattableDocument document) {
        document.prepend(regionFor(operation).keyword("inline"), this::newLine);
    }

    protected void format(InlineIfOperation operation, IFormattableDocument document) {
        document.prepend(regionFor(operation).keyword("inline"), this::newLine);
        document.format(operation.getBody());

        if (operation.getElse() != null) {
            document.format(operation.getElse());
        }
    }

    protected void format(Property property, IFormattableDocument document) {
        document.prepend(regionFor(property).keyword("prop"), this::newLine);
        setupBrackets(property, document);
    }

    protected void format(Pattern pattern, IFormattableDocument document) {
        document.prepend(regionFor(pattern).keyword("pattern"), (a) -> {
            a.setNewLines(1, 1, 2);
        });

        for (var body : pattern.getPatternBodies()) {
            document.format(body);
        }
    }

    protected void format(PatternBody body, IFormattableDocument document) {
        setupBrackets(body, document);

        for (var constraint : body.getConstraints()) {
            document.format(constraint);
        }
    }

    protected void format(TypeConstraint constraint, IFormattableDocument document) {
        if (constraint.isNegated()) {
            document.prepend(regionFor(constraint).keyword("neg"), this::newLine);
        } else {
            document.prepend(regionFor(constraint).feature(OxstsPackage.Literals.TYPE_CONSTRAINT__TYPE), this::newLine);
        }
    }

    protected void format(FeatureConstraint constraint, IFormattableDocument document) {
        if (constraint.isNegated()) {
            document.prepend(regionFor(constraint).keyword("neg"), this::newLine);
        } else {
            document.prepend(regionFor(constraint).feature(OxstsPackage.Literals.TYPE_CONSTRAINT__TYPE), this::newLine);
        }
    }

    protected void format(PatternConstraint constraint, IFormattableDocument document) {
        if (constraint.isNegated()) {
            document.prepend(regionFor(constraint).keyword("neg"), this::newLine);
        } else {
            document.prepend(regionFor(constraint).keyword("find"), this::newLine);
        }
    }

    protected void format(OperatorExpression expression, IFormattableDocument document) {
        for (Expression operand : expression.getOperands()) {
            document.format(operand);
        }
    }

    protected void format(ChainReferenceExpression expression, IFormattableDocument document) {
        document.prepend(allRegionsFor(expression).keyword("."), this::noSpace);
        document.append(allRegionsFor(expression).keyword("."), this::noSpace);

        for (var expr : expression.getChains()) {
            document.format(expr);
        }
    }

    protected void setupBrackets(EObject eObject, IFormattableDocument document) {
        document.prepend(regionFor(eObject).keyword("{"), this::oneSpace);
        document.prepend(regionFor(eObject).keyword("}"), this::newLine);
        document.interior(regionFor(eObject).keyword("{"), regionFor(eObject).keyword("}"), this::indent);
    }

}
