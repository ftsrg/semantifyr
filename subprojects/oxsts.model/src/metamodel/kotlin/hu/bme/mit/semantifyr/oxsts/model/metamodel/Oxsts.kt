/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.model.metamodel

import hu.bme.mit.semantifyr.oxsts.model.metamodel.Bounds.MANY
import hu.bme.mit.semantifyr.oxsts.model.metamodel.Bounds.ONE
import hu.bme.mit.semantifyr.oxsts.model.metamodel.Bounds.ONE_OR_MORE

fun oxsts() = metamodel(
    name = "oxsts",
    nsUri = "http://www.bme.hu/mit/2023/oxsts",
    basePackage = "hu.bme.mit.semantifyr.oxsts.model",
    complianceLevel = "17.0",
) {
    abstractEClass("Element")

    eClass("OxstsModelPackage", "Namespace") {
        contains("imports", "ImportStatement", MANY)
        contains("declarations", "Declaration", MANY)
    }

    eClass("TransitionDeclaration", "ParametricDeclaration", "RedefinableDeclaration") {
        contains("branches", "SequenceOperation", MANY)
        attribute("kind", "TransitionKind", ONE)
        attribute("abstract", "Boolean", ONE, default = "false")
    }

    eClass("SequenceOperation", "Namespace", "Operation") {
        contains("steps", "Operation", MANY)
    }

    abstractEClass("Operation", "Element")

    eClass("HavocOperation", "Operation") {
        contains("reference", "Expression", ONE)
    }

    eClass("AssignmentOperation", "Operation") {
        contains("reference", "Expression", ONE)
        contains("expression", "Expression", ONE)
    }

    eClass("ChoiceOperation", "Operation") {
        contains("branches", "SequenceOperation", ONE_OR_MORE)
    }

    eClass("FeatureDeclaration", "DomainDeclaration", "Namespace", "RedefinableDeclaration") {
        refers("superset", "FeatureDeclaration")
        refers("opposite", "FeatureDeclaration")
        contains("expression", "Expression")
        attribute("kind", "FeatureKind", ONE, default = "REFERENCE")
        contains("innerFeatures", "FeatureDeclaration", MANY)
        contains("typeSpecification", "TypeSpecification")
    }

    eClass("ClassDeclaration", "TopLevelDeclaration", "Namespace", "TypeDeclaration") {
        refers("superTypes", "ClassDeclaration", MANY)
        attribute("abstract", "Boolean", ONE, default = "false")
        contains("members", "Declaration", MANY)
    }

    eClass("InlineCall", "InlineOperation") {
        contains("callExpression", "Expression")
    }

    abstractEClass("Expression", "Element")
    abstractEClass("LiteralExpression", "Expression")

    eClass("LiteralInteger", "LiteralExpression") {
        attribute("value", "Int", ONE)
    }

    eClass("LiteralBoolean", "LiteralExpression") {
        attribute("value", "Boolean", ONE)
    }

    eClass("LiteralNothing", "LiteralExpression")
    abstractEClass("ReferenceExpression", "Expression")
    abstractEClass("OperatorExpression", "Expression")

    eClass("AssumptionOperation", "Operation") {
        contains("expression", "Expression", ONE)
    }

    abstractEClass("Multiplicity", "Element")

    eClass("SelfReference", "DirectReferenceExpression") {
        refers("class", "ClassDeclaration")
    }

    eClass("ElementReference", "DirectReferenceExpression") {
        refers("element", "NamedElement", ONE)
    }

    abstractEClass("InlineOperation", "Operation")
    abstractEClass("Namespace", "NamedElement")

    eClass("IfOperation", "Operation") {
        contains("guard", "Expression", ONE)
        contains("body", "SequenceOperation", ONE)
        contains("else", "SequenceOperation")
    }

    eClass("InlineIfOperation", "InlineOperation") {
        contains("guard", "Expression")
        contains("body", "SequenceOperation", ONE)
        contains("else", "SequenceOperation")
    }

    eClass("EnumLiteral", "NamedElement")

    eClass("EnumDeclaration", "Namespace", "TopLevelDeclaration", "TypeDeclaration") {
        contains("literals", "EnumLiteral", ONE_OR_MORE)
    }

    eClass("PropertyDeclaration", "ParametricDeclaration", "RedefinableDeclaration") {
        contains("expression", "Expression")
        attribute("abstract", "Boolean", ONE, default = "false")
        contains("returnType", "TypeSpecification")
    }

    eClass("VariableDeclaration", "Declaration") {
        contains("expression", "Expression")
        contains("typeSpecification", "TypeSpecification")
    }

    eClass("TargetDeclaration", "ClassDeclaration")

    eClass("RecordDeclaration", "Namespace", "TopLevelDeclaration", "TypeDeclaration") {
        contains("members", "Declaration", MANY)
    }

    eClass("ImportStatement", "Element") {
        refers("importedPackage", "OxstsModelPackage", ONE)
    }

    eClass("PatternDeclaration", "Namespace", "TopLevelDeclaration", "ParametricDeclaration") {
        contains("bodies", "Conjunction", MANY)
    }

    eClass("Conjunction", "Element")
    eEnum("TransitiveClosureKind", "None", "WithoutSelf", "IncludeSelf")

    eClass("ParameterDeclaration", "NamedElement") {
        contains("typeSpecification", "TypeSpecification")
    }

    abstractEClass("BinaryOperator", "OperatorExpression") {
        contains("left", "Expression", ONE)
        contains("right", "Expression", ONE)
    }

    abstractEClass("UnaryOperator", "OperatorExpression") {
        contains("body", "Expression", ONE)
    }

    eEnum("UnaryOp", "PLUS", "MINUS")
    eEnum("ArithmeticOp", "ADD", "SUB", "MUL", "DIV")

    eClass("ComparisonOperator", "BinaryOperator") {
        attribute("op", "ComparisonOp", ONE)
    }

    eClass("ArithmeticBinaryOperator", "BinaryOperator") {
        attribute("op", "ArithmeticOp", ONE)
    }

    eClass("ArithmeticUnaryOperator", "UnaryOperator") {
        attribute("op", "UnaryOp", ONE)
    }

    eClass("NegationOperator", "UnaryOperator")
    eEnum("ComparisonOp", "LESS", "LESS_EQ", "GREATER", "GREATER_EQ", "EQ", "NOT_EQ")
    eClass("UnboundedMultiplicity", "Multiplicity")

    eClass("DefiniteMultiplicity", "Multiplicity") {
        contains("expression", "Expression")
    }

    abstractEClass("NamedElement", "Element") {
        attribute("name", "EString")
    }

    eClass("BooleanOperator", "BinaryOperator") {
        attribute("op", "BooleanOp", ONE)
    }

    eEnum("BooleanOp", "AND" to 4, "OR" to 5, "XOR" to 6)

    eClass("NavigationSuffixExpression", "PostfixUnaryExpression") {
        refers("member", "NamedElement", ONE)
        attribute("optional", "Boolean", ONE, default = "false")
    }

    eClass("IndexingSuffixExpression", "PostfixUnaryExpression") {
        contains("index", "Expression", ONE)
    }

    eClass("CallSuffixExpression", "PostfixUnaryExpression") {
        contains("arguments", "Argument", MANY)
    }

    eClass("ArrayLiteral", "LiteralExpression") {
        contains("values", "Expression", MANY)
    }

    eClass("Argument") {
        refers("parameter", "ParameterDeclaration")
        contains("expression", "Expression", ONE)
    }

    eClass("LiteralReal", "LiteralExpression") {
        attribute("value", "Double", ONE)
    }

    eClass("FunctionDeclaration", "ParametricDeclaration", "TopLevelDeclaration") {
        refers("returnType", "TypeDeclaration")
    }

    abstractEClass("ParametricDeclaration", "Declaration", "Namespace") {
        contains("parameters", "ParameterDeclaration", MANY)
    }

    abstractEClass("TopLevelDeclaration", "Declaration")
    abstractEClass("Declaration", "NamedElement", "AnnotatedElement")
    eClass("AnnotationDeclaration", "TopLevelDeclaration", "ParametricDeclaration")

    eClass("Annotation") {
        refers("declaration", "AnnotationDeclaration", ONE)
        contains("arguments", "Argument", MANY)
    }

    eEnum("TransitionKind", "TRAN", "ENV", "INIT", "HAVOC")

    eClass("AnnotationContainer", "Declaration") {
        contains("annotations", "Annotation", MANY)
    }

    abstractEClass("AnnotatedElement", "NamedElement") {
        contains("annotation", "AnnotationContainer")
    }

    eEnum("FeatureKind", "REFERENCE", "CONTAINMENT", "CONTAINER", "DERIVED", "FEATURE")

    eClass("LiteralString", "LiteralExpression") {
        attribute("value", "String", ONE)
    }

    eClass("DataTypeDeclaration", "TopLevelDeclaration", "TypeDeclaration")
    abstractEClass("TypeDeclaration", "Declaration", "DomainDeclaration")
    abstractEClass("DomainDeclaration", "Declaration")
    abstractEClass("DirectReferenceExpression", "ReferenceExpression")

    abstractEClass("PostfixUnaryExpression", "ReferenceExpression") {
        contains("primary", "Expression", ONE)
    }

    eClass("InlineSeqFor", "InlineForOperation")
    eClass("InlineChoiceFor", "InlineForOperation")

    abstractEClass("AbstractForOperation", "Operation", "Namespace") {
        contains("loopVariable", "VariableDeclaration")
        contains("rangeExpression", "Expression", ONE)
        contains("body", "SequenceOperation", ONE)
    }

    eClass("LocalVarDeclarationOperation", "VariableDeclaration", "Operation")
    eClass("ForOperation", "AbstractForOperation")

    abstractEClass("InlineForOperation", "AbstractForOperation", "InlineOperation") {
        contains("else", "SequenceOperation")
    }

    eClass("RangeExpression", "BinaryOperator") {
        attribute("exclusive", "Boolean", ONE, default = "false")
    }

    eClass("LiteralInfinity", "LiteralExpression")

    eClass("RedefinableDeclaration", "Declaration") {
        attribute("redefine", "Boolean", default = "false")
        refers("redefined", "RedefinableDeclaration")
    }

    eClass("InlinedOxsts", "DomainDeclaration") {
        contains("initTransition", "TransitionDeclaration", ONE)
        contains("mainTransition", "TransitionDeclaration", ONE)
        contains("property", "PropertyDeclaration", ONE)
        contains("variables", "VariableDeclaration", MANY)
        refers("classDeclaration", "ClassDeclaration", ONE)
        contains("rootFeature", "FeatureDeclaration")
        attribute("witness", "Boolean", ONE, default = "false")
    }

    eClass("TypeSpecification") {
        contains("multiplicity", "Multiplicity")
        refers("domain", "DomainDeclaration")
    }

    abstractEClass("TemporalOperator", "OperatorExpression") {
        contains("body", "Expression", ONE)
    }

    eClass("AG", "TemporalOperator")
    eClass("EF", "TemporalOperator")

    eClass("CastExpression", "UnaryOperator") {
        contains("typespecification", "TypeSpecification", ONE)
    }

    eClass("IfThenElse", "OperatorExpression") {
        contains("guard", "Expression", ONE)
        contains("then", "Expression", ONE)
        contains("else", "Expression", ONE)
    }
}
