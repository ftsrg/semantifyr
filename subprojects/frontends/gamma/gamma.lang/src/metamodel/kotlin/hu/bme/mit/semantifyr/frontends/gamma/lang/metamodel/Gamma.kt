/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.metamodel

import hu.bme.mit.semantifyr.oxsts.model.metamodel.AbstractMetaClass
import hu.bme.mit.semantifyr.oxsts.model.metamodel.Bounds.MANY
import hu.bme.mit.semantifyr.oxsts.model.metamodel.EBoolean
import hu.bme.mit.semantifyr.oxsts.model.metamodel.EInt
import hu.bme.mit.semantifyr.oxsts.model.metamodel.EString
import hu.bme.mit.semantifyr.oxsts.model.metamodel.MetaClass
import hu.bme.mit.semantifyr.oxsts.model.metamodel.MetaEnum
import hu.bme.mit.semantifyr.oxsts.model.metamodel.MetaPackage

object Gamma : MetaPackage(
    name = "gamma",
    nsUri = "http://www.bme.hu/mit/2025/gamma",
    basePackage = "hu.bme.mit.semantifyr.frontends.gamma.lang",
    complianceLevel = "17.0",
) {
    object NamedElement : AbstractMetaClass() {
        val name by attribute(EString)
    }

    object GammaRoot : AbstractMetaClass(NamedElement) {
        val imports by contains(MANY) { ImportStatement }
    }

    object ImportStatement : MetaClass() {
        val importedPackage by refers { GammaModelPackage }
    }

    object GammaModelPackage : MetaClass(GammaRoot) {
        val typeDeclarations by contains(MANY) { TypeDeclaration }
        val interfaces by contains(MANY) { InterfaceDeclaration }
        val components by contains(MANY) { ComponentDeclaration }
        val verificationCases by contains(MANY) { VerificationCaseDeclaration }
    }

    object TypeDeclaration : MetaClass(NamedElement) {
        val type by contains { Type }
    }

    object Type : AbstractMetaClass()

    object IntegerType : MetaClass(Type)

    object BooleanType : MetaClass(Type)

    object RecordType : MetaClass(Type) {
        val fields by contains(MANY) { FieldDeclaration }
    }

    object TypeReference : MetaClass(Type) {
        val reference by refers { TypeDeclaration }
    }

    object ValueDeclaration : AbstractMetaClass(NamedElement) {
        val type by contains { Type }
    }

    object FieldDeclaration : MetaClass(ValueDeclaration)

    object ParameterDeclaration : MetaClass(ValueDeclaration)

    object VariableDeclaration : MetaClass(ValueDeclaration) {
        val default by contains { Expression }
    }

    object InterfaceDeclaration : MetaClass(NamedElement) {
        val parents by refers(MANY) { InterfaceDeclaration }
        val events by contains(MANY) { EventDeclaration }
    }

    enum class EventDirection {
        IN,
        OUT,
        INOUT,
        ;

        companion object : MetaEnum<EventDirection>(entries)
    }

    enum class Persistency {
        TRANSIENT,
        PERSISTENT,
        ;

        companion object : MetaEnum<Persistency>(entries)
    }

    object EventDeclaration : MetaClass(NamedElement) {
        val direction by attribute(EventDirection)
        val persistency by attribute(Persistency)
        val parameters by contains(MANY) { ParameterDeclaration }
    }

    object ComponentDeclaration : AbstractMetaClass(NamedElement) {
        val ports by contains(MANY) { PortDeclaration }
    }

    object CompositeComponentDeclaration : AbstractMetaClass(ComponentDeclaration) {
        val components by contains(MANY) { ComponentInstance }
        val channels by contains(MANY) { Channel }
        val bindings by contains(MANY) { PortBinding }
    }

    object SyncComponentDeclaration : MetaClass(CompositeComponentDeclaration)

    object CascadeComponentDeclaration : MetaClass(CompositeComponentDeclaration) {
        val executionList by refers(MANY) { ComponentInstance }
    }

    object ComponentInstance : MetaClass(NamedElement) {
        val component by refers { ComponentDeclaration }
    }

    enum class RealizationMode {
        PROVIDED,
        REQUIRED,
        ;

        companion object : MetaEnum<RealizationMode>(entries)
    }

    object PortDeclaration : MetaClass(NamedElement) {
        val realizationMode by attribute(RealizationMode)
        val `interface` by refers { InterfaceDeclaration }
    }

    object InstancePortReference : MetaClass() {
        val instance by refers { ComponentInstance }
        val port by refers { PortDeclaration }
    }

    object PortBinding : MetaClass() {
        val port by refers { PortDeclaration }
        val boundPort by contains { InstancePortReference }
    }

    object Channel : MetaClass() {
        val providedPort by contains { InstancePortReference }
        val requiredPort by contains { InstancePortReference }
    }

    enum class SchedulingOrder {
        TOP_DOWN,
        BOTTOM_UP,
        ;

        companion object : MetaEnum<SchedulingOrder>(entries)
    }

    enum class TransitionPriority {
        OFF,
        ORDER_BASED,
        VALUE_BASED,
        ;

        companion object : MetaEnum<TransitionPriority>(entries)
    }

    enum class GuardEvaluation {
        ON_THE_FLY,
        BEGINNING_OF_STEP,
        ;

        companion object : MetaEnum<GuardEvaluation>(entries)
    }

    object StatechartDeclaration : MetaClass(ComponentDeclaration) {
        val schedulingOrder by attribute(SchedulingOrder)
        val transitionPriority by attribute(TransitionPriority)
        val guardEvaluation by attribute(GuardEvaluation)
        val variables by contains(MANY) { VariableDeclaration }
        val timeouts by contains(MANY) { TimeoutDeclaration }
        val regions by contains(MANY) { Region }
        val transitions by contains(MANY) { Transition }
    }

    object TimeoutDeclaration : MetaClass(NamedElement)

    object Region : MetaClass(NamedElement) {
        val stateNodes by contains(MANY) { StateNode }
    }

    object StateNode : AbstractMetaClass(NamedElement)

    object EntryState : AbstractMetaClass(StateNode)

    object InitialState : MetaClass(EntryState)

    object ShallowHistoryState : MetaClass(EntryState)

    object State : MetaClass(StateNode) {
        val entryActions by contains(MANY) { Action }
        val exitActions by contains(MANY) { Action }
        val regions by contains(MANY) { Region }
    }

    object Transition : MetaClass(NamedElement) {
        val priority by attribute(EInt)
        val from by refers { StateNode }
        val to by refers { StateNode }
        val trigger by contains { Trigger }
        val guard by contains { Expression }
        val actions by contains(MANY) { Action }
    }

    object Trigger : AbstractMetaClass()

    object TimeoutTrigger : MetaClass(Trigger) {
        val timeout by refers { TimeoutDeclaration }
    }

    object EventTrigger : MetaClass(Trigger) {
        val port by refers { PortDeclaration }
        val event by refers { EventDeclaration }
    }

    object Action : AbstractMetaClass()

    object Block : MetaClass(Action) {
        val actions by contains(MANY) { Action }
    }

    object RaiseEventAction : MetaClass(Action) {
        val port by refers { PortDeclaration }
        val event by refers { EventDeclaration }
        val arguments by contains(MANY) { Expression }
    }

    enum class TimeUnit {
        MILLISECOND,
        SECOND,
        ;

        companion object : MetaEnum<TimeUnit>(entries)
    }

    object SetTimeoutAction : MetaClass(Action) {
        val timeout by refers { TimeoutDeclaration }
        val value by contains { Expression }
        val unit by attribute(TimeUnit)
    }

    object AssignmentAction : MetaClass(Action) {
        val reference by contains { Expression }
        val expression by contains { Expression }
    }

    object VerificationCaseDeclaration : MetaClass(NamedElement) {
        val component by refers { ComponentDeclaration }
        val expression by contains { Expression }
    }

    object ExecutionTrace : MetaClass(GammaRoot) {
        val verificationCase by refers { VerificationCaseDeclaration }
        val steps by contains(MANY) { Step }
    }

    object Step : MetaClass() {
        val acts by contains(MANY) { Act }
        val asserts by contains(MANY) { Expression }
    }

    object Act : AbstractMetaClass()

    object ResetAct : MetaClass(Act)

    object ScheduleAct : MetaClass(Act)

    object TimeElapseAct : MetaClass(Act) {
        val time by attribute(EInt)
    }

    object RaiseEventAct : MetaClass(Act, InstanceElementExpression) {
        val port by refers { PortDeclaration }
        val event by refers { EventDeclaration }
        val arguments by contains(MANY) { Expression }
    }

    object Expression : AbstractMetaClass()

    object TemporalOperator : AbstractMetaClass(Expression) {
        val body by contains { Expression }
    }

    object AG : MetaClass(TemporalOperator)

    object EF : MetaClass(TemporalOperator)

    enum class BooleanOp {
        AND,
        OR,
        ;

        companion object : MetaEnum<BooleanOp>(entries)
    }

    object BooleanOperator : MetaClass(Expression) {
        val left by contains { Expression }
        val op by attribute(BooleanOp)
        val right by contains { Expression }
    }

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

    object ComparisonOperator : MetaClass(Expression) {
        val left by contains { Expression }
        val op by attribute(ComparisonOp)
        val right by contains { Expression }
    }

    enum class ArithmeticOp {
        ADD,
        SUB,
        MUL,
        DIV,
        ;

        companion object : MetaEnum<ArithmeticOp>(entries)
    }

    object ArithmeticBinaryOperator : MetaClass(Expression) {
        val left by contains { Expression }
        val op by attribute(ArithmeticOp)
        val right by contains { Expression }
    }

    object NegationOperator : MetaClass(Expression) {
        val body by contains { Expression }
    }

    enum class UnaryOp {
        PLUS,
        MINUS,
        ;

        companion object : MetaEnum<UnaryOp>(entries)
    }

    object ArithmeticUnaryOperator : MetaClass(Expression) {
        val op by attribute(UnaryOp)
        val body by contains { Expression }
    }

    object LiteralExpression : AbstractMetaClass(Expression)

    object LiteralBoolean : MetaClass(LiteralExpression) {
        val value by attribute(EBoolean)
    }

    object LiteralInteger : MetaClass(LiteralExpression) {
        val value by attribute(EInt)
    }

    object DirectReferenceExpression : MetaClass(Expression) {
        val declaration by refers { ValueDeclaration }
    }

    object EventParameterReferenceExpression : MetaClass(Expression) {
        val port by refers { PortDeclaration }
        val event by refers { EventDeclaration }
        val parameter by refers { ParameterDeclaration }
    }

    object RecordAccessExpression : MetaClass(Expression) {
        val operand by contains { Expression }
        val field by refers { FieldDeclaration }
    }

    object ComponentInstanceReference : MetaClass() {
        val instance by refers { ComponentInstance }
        val child by contains { ComponentInstanceReference }
    }

    object InstanceElementExpression : AbstractMetaClass(Expression) {
        val instance by contains { ComponentInstanceReference }
    }

    object StateReferenceExpression : MetaClass(InstanceElementExpression) {
        val region by refers { Region }
        val state by refers { State }
    }

    object VariableReferenceExpression : MetaClass(InstanceElementExpression) {
        val variable by refers { VariableDeclaration }
    }

    object FiredTransitionExpression : MetaClass(InstanceElementExpression) {
        val transition by refers { Transition }
        val from by refers { State }
        val to by refers { State }
        val trigger by contains { Trigger }
    }
}
