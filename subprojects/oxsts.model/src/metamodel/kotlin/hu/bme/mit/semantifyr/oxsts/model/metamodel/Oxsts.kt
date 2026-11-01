/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.model.metamodel

import hu.bme.mit.semantifyr.oxsts.model.metamodel.Bounds.MANY
import hu.bme.mit.semantifyr.oxsts.model.metamodel.Bounds.ONE
import hu.bme.mit.semantifyr.oxsts.model.metamodel.Bounds.ONE_OR_MORE

val oxsts = MetaPackage(
    name = "oxsts",
    nsUri = "http://www.bme.hu/mit/2023/oxsts",
    basePackage = "hu.bme.mit.semantifyr.oxsts.model",
    complianceLevel = "17.0",
    classifiers = listOf(
        Element,
        OxstsModelPackage,
        TransitionDeclaration,
        SequenceOperation,
        Operation,
        HavocOperation,
        AssignmentOperation,
        ChoiceOperation,
        FeatureDeclaration,
        ClassDeclaration,
        InlineCall,
        Expression,
        LiteralExpression,
        LiteralInteger,
        LiteralBoolean,
        LiteralNothing,
        ReferenceExpression,
        OperatorExpression,
        AssumptionOperation,
        Multiplicity,
        SelfReference,
        ElementReference,
        InlineOperation,
        Namespace,
        IfOperation,
        InlineIfOperation,
        EnumLiteral,
        EnumDeclaration,
        PropertyDeclaration,
        VariableDeclaration,
        TargetDeclaration,
        RecordDeclaration,
        ImportStatement,
        PatternDeclaration,
        Conjunction,
        TransitiveClosureKind,
        ParameterDeclaration,
        BinaryOperator,
        UnaryOperator,
        UnaryOp,
        ArithmeticOp,
        ComparisonOperator,
        ArithmeticBinaryOperator,
        ArithmeticUnaryOperator,
        NegationOperator,
        ComparisonOp,
        UnboundedMultiplicity,
        DefiniteMultiplicity,
        NamedElement,
        BooleanOperator,
        BooleanOp,
        NavigationSuffixExpression,
        IndexingSuffixExpression,
        CallSuffixExpression,
        ArrayLiteral,
        Argument,
        LiteralReal,
        FunctionDeclaration,
        ParametricDeclaration,
        TopLevelDeclaration,
        Declaration,
        AnnotationDeclaration,
        Annotation,
        TransitionKind,
        AnnotationContainer,
        AnnotatedElement,
        FeatureKind,
        LiteralString,
        DataTypeDeclaration,
        TypeDeclaration,
        DomainDeclaration,
        DirectReferenceExpression,
        PostfixUnaryExpression,
        InlineSeqFor,
        InlineChoiceFor,
        AbstractForOperation,
        LocalVarDeclarationOperation,
        ForOperation,
        InlineForOperation,
        RangeExpression,
        LiteralInfinity,
        RedefinableDeclaration,
        InlinedOxsts,
        TypeSpecification,
        TemporalOperator,
        AG,
        EF,
        CastExpression,
        IfThenElse,
    ),
)

object Element : AbstractMetaClass()

object OxstsModelPackage : MetaClass(Namespace) {
    val imports by contains(MANY) { ImportStatement }
    val declarations by contains(MANY) { Declaration }
}

object TransitionDeclaration : MetaClass(ParametricDeclaration, RedefinableDeclaration) {
    val branches by contains(MANY) { SequenceOperation }
    val kind by attribute(TransitionKind, ONE)
    val abstract by attribute(EBoolean, ONE, default = false)
}

object SequenceOperation : MetaClass(Namespace, Operation) {
    val steps by contains(MANY) { Operation }
}

object Operation : AbstractMetaClass(Element)

object HavocOperation : MetaClass(Operation) {
    val reference by contains(ONE) { Expression }
}

object AssignmentOperation : MetaClass(Operation) {
    val reference by contains(ONE) { Expression }
    val expression by contains(ONE) { Expression }
}

object ChoiceOperation : MetaClass(Operation) {
    val branches by contains(ONE_OR_MORE) { SequenceOperation }
}

object FeatureDeclaration : MetaClass(DomainDeclaration, Namespace, RedefinableDeclaration) {
    val superset by refers { FeatureDeclaration }
    val opposite by refers { FeatureDeclaration }
    val expression by contains { Expression }
    val kind by attribute(FeatureKind, ONE, default = FeatureKind.REFERENCE)
    val innerFeatures by contains(MANY) { FeatureDeclaration }
    val typeSpecification by contains { TypeSpecification }
}

object ClassDeclaration : MetaClass(TopLevelDeclaration, Namespace, TypeDeclaration) {
    val superTypes by refers(MANY) { ClassDeclaration }
    val abstract by attribute(EBoolean, ONE, default = false)
    val members by contains(MANY) { Declaration }
}

object InlineCall : MetaClass(InlineOperation) {
    val callExpression by contains { Expression }
}

object Expression : AbstractMetaClass(Element)

object LiteralExpression : AbstractMetaClass(Expression)

object LiteralInteger : MetaClass(LiteralExpression) {
    val value by attribute(EInt, ONE)
}

object LiteralBoolean : MetaClass(LiteralExpression) {
    val value by attribute(EBoolean, ONE)
}

object LiteralNothing : MetaClass(LiteralExpression)

object ReferenceExpression : AbstractMetaClass(Expression)

object OperatorExpression : AbstractMetaClass(Expression)

object AssumptionOperation : MetaClass(Operation) {
    val expression by contains(ONE) { Expression }
}

object Multiplicity : AbstractMetaClass(Element)

object SelfReference : MetaClass(DirectReferenceExpression) {
    val `class` by refers { ClassDeclaration }
}

object ElementReference : MetaClass(DirectReferenceExpression) {
    val element by refers(ONE) { NamedElement }
}

object InlineOperation : AbstractMetaClass(Operation)

object Namespace : AbstractMetaClass(NamedElement)

object IfOperation : MetaClass(Operation) {
    val guard by contains(ONE) { Expression }
    val body by contains(ONE) { SequenceOperation }
    val `else` by contains { SequenceOperation }
}

object InlineIfOperation : MetaClass(InlineOperation) {
    val guard by contains { Expression }
    val body by contains(ONE) { SequenceOperation }
    val `else` by contains { SequenceOperation }
}

object EnumLiteral : MetaClass(NamedElement)

object EnumDeclaration : MetaClass(Namespace, TopLevelDeclaration, TypeDeclaration) {
    val literals by contains(ONE_OR_MORE) { EnumLiteral }
}

object PropertyDeclaration : MetaClass(ParametricDeclaration, RedefinableDeclaration) {
    val expression by contains { Expression }
    val abstract by attribute(EBoolean, ONE, default = false)
    val returnType by contains { TypeSpecification }
}

object VariableDeclaration : MetaClass(Declaration) {
    val expression by contains { Expression }
    val typeSpecification by contains { TypeSpecification }
}

object TargetDeclaration : MetaClass(ClassDeclaration)

object RecordDeclaration : MetaClass(Namespace, TopLevelDeclaration, TypeDeclaration) {
    val members by contains(MANY) { Declaration }
}

object ImportStatement : MetaClass(Element) {
    val importedPackage by refers(ONE) { OxstsModelPackage }
}

object PatternDeclaration : MetaClass(Namespace, TopLevelDeclaration, ParametricDeclaration) {
    val bodies by contains(MANY) { Conjunction }
}

object Conjunction : MetaClass(Element)

enum class TransitiveClosureKind {
    None,
    WithoutSelf,
    IncludeSelf,
    ;

    companion object : MetaEnum<TransitiveClosureKind>(entries)
}

object ParameterDeclaration : MetaClass(NamedElement) {
    val typeSpecification by contains { TypeSpecification }
}

object BinaryOperator : AbstractMetaClass(OperatorExpression) {
    val left by contains(ONE) { Expression }
    val right by contains(ONE) { Expression }
}

object UnaryOperator : AbstractMetaClass(OperatorExpression) {
    val body by contains(ONE) { Expression }
}

enum class UnaryOp {
    PLUS,
    MINUS,
    ;

    companion object : MetaEnum<UnaryOp>(entries)
}

enum class ArithmeticOp {
    ADD,
    SUB,
    MUL,
    DIV,
    ;

    companion object : MetaEnum<ArithmeticOp>(entries)
}

object ComparisonOperator : MetaClass(BinaryOperator) {
    val op by attribute(ComparisonOp, ONE)
}

object ArithmeticBinaryOperator : MetaClass(BinaryOperator) {
    val op by attribute(ArithmeticOp, ONE)
}

object ArithmeticUnaryOperator : MetaClass(UnaryOperator) {
    val op by attribute(UnaryOp, ONE)
}

object NegationOperator : MetaClass(UnaryOperator)

enum class ComparisonOp {
    LESS,
    LESS_EQ,
    GREATER,
    GREATER_EQ,
    EQ,
    NOT_EQ,
    ;

    companion object : MetaEnum<ComparisonOp>(entries)
}

object UnboundedMultiplicity : MetaClass(Multiplicity)

object DefiniteMultiplicity : MetaClass(Multiplicity) {
    val expression by contains { Expression }
}

object NamedElement : AbstractMetaClass(Element) {
    val name by attribute(EString)
}

object BooleanOperator : MetaClass(BinaryOperator) {
    val op by attribute(BooleanOp, ONE)
}

enum class BooleanOp(val value: Int) {
    AND(4),
    OR(5),
    XOR(6),
    ;

    companion object : MetaEnum<BooleanOp>(entries, BooleanOp::value)
}

object NavigationSuffixExpression : MetaClass(PostfixUnaryExpression) {
    val member by refers(ONE) { NamedElement }
    val optional by attribute(EBoolean, ONE, default = false)
}

object IndexingSuffixExpression : MetaClass(PostfixUnaryExpression) {
    val index by contains(ONE) { Expression }
}

object CallSuffixExpression : MetaClass(PostfixUnaryExpression) {
    val arguments by contains(MANY) { Argument }
}

object ArrayLiteral : MetaClass(LiteralExpression) {
    val values by contains(MANY) { Expression }
}

object Argument : MetaClass() {
    val parameter by refers { ParameterDeclaration }
    val expression by contains(ONE) { Expression }
}

object LiteralReal : MetaClass(LiteralExpression) {
    val value by attribute(EDouble, ONE)
}

object FunctionDeclaration : MetaClass(ParametricDeclaration, TopLevelDeclaration) {
    val returnType by refers { TypeDeclaration }
}

object ParametricDeclaration : AbstractMetaClass(Declaration, Namespace) {
    val parameters by contains(MANY) { ParameterDeclaration }
}

object TopLevelDeclaration : AbstractMetaClass(Declaration)

object Declaration : AbstractMetaClass(NamedElement, AnnotatedElement)

object AnnotationDeclaration : MetaClass(TopLevelDeclaration, ParametricDeclaration)

object Annotation : MetaClass() {
    val declaration by refers(ONE) { AnnotationDeclaration }
    val arguments by contains(MANY) { Argument }
}

enum class TransitionKind(val value: Int) {
    TRAN(0),
    INIT(2),
    ;

    companion object : MetaEnum<TransitionKind>(entries, TransitionKind::value)
}

object AnnotationContainer : MetaClass(Declaration) {
    val annotations by contains(MANY) { Annotation }
}

object AnnotatedElement : AbstractMetaClass(NamedElement) {
    val annotation by contains { AnnotationContainer }
}

enum class FeatureKind {
    REFERENCE,
    CONTAINMENT,
    CONTAINER,
    DERIVED,
    FEATURE,
    ;

    companion object : MetaEnum<FeatureKind>(entries)
}

object LiteralString : MetaClass(LiteralExpression) {
    val value by attribute(EString, ONE)
}

object DataTypeDeclaration : MetaClass(TopLevelDeclaration, TypeDeclaration)

object TypeDeclaration : AbstractMetaClass(Declaration, DomainDeclaration)

object DomainDeclaration : AbstractMetaClass(Declaration)

object DirectReferenceExpression : AbstractMetaClass(ReferenceExpression)

object PostfixUnaryExpression : AbstractMetaClass(ReferenceExpression) {
    val primary by contains(ONE) { Expression }
}

object InlineSeqFor : MetaClass(InlineForOperation)

object InlineChoiceFor : MetaClass(InlineForOperation)

object AbstractForOperation : AbstractMetaClass(Operation, Namespace) {
    val loopVariable by contains { VariableDeclaration }
    val rangeExpression by contains(ONE) { Expression }
    val body by contains(ONE) { SequenceOperation }
}

object LocalVarDeclarationOperation : MetaClass(VariableDeclaration, Operation)

object ForOperation : MetaClass(AbstractForOperation)

object InlineForOperation : AbstractMetaClass(AbstractForOperation, InlineOperation) {
    val `else` by contains { SequenceOperation }
}

object RangeExpression : MetaClass(BinaryOperator) {
    val exclusive by attribute(EBoolean, ONE, default = false)
}

object LiteralInfinity : MetaClass(LiteralExpression)

object RedefinableDeclaration : MetaClass(Declaration) {
    val redefine by attribute(EBoolean, default = false)
    val redefined by refers { RedefinableDeclaration }
}

object InlinedOxsts : MetaClass(DomainDeclaration) {
    val initTransition by contains(ONE) { TransitionDeclaration }
    val mainTransition by contains(ONE) { TransitionDeclaration }
    val property by contains(ONE) { PropertyDeclaration }
    val variables by contains(MANY) { VariableDeclaration }
    val classDeclaration by refers(ONE) { ClassDeclaration }
    val rootFeature by contains { FeatureDeclaration }
    val witness by attribute(EBoolean, ONE, default = false)
}

object TypeSpecification : MetaClass() {
    val multiplicity by contains { Multiplicity }
    val domain by refers { DomainDeclaration }
}

object TemporalOperator : AbstractMetaClass(OperatorExpression) {
    val body by contains(ONE) { Expression }
}

object AG : MetaClass(TemporalOperator)

object EF : MetaClass(TemporalOperator)

object CastExpression : MetaClass(UnaryOperator) {
    val typespecification by contains(ONE) { TypeSpecification }
}

object IfThenElse : MetaClass(OperatorExpression) {
    val guard by contains(ONE) { Expression }
    val then by contains(ONE) { Expression }
    val `else` by contains(ONE) { Expression }
}
