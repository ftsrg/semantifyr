/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.witness

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.witness.Fraction
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceReferenceProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.RedefinitionAwareReferenceResolver
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.compiler.pipeline.utils.treeSequence
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.lang.semantics.clock.ClockTestAnalysis
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureKind
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineCall
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PostfixUnaryExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RecordDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionKind
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.verifier.ReportState
import hu.bme.mit.semantifyr.verifier.ReportTrace
import hu.bme.mit.semantifyr.verifier.ReportValue
import java.math.BigInteger

class ClassWitnessBackAnnotator @Inject constructor(
    private val builtinSymbolResolver: BuiltinSymbolResolver,
    private val redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver,
    private val classWitnessTransformer: ClassWitnessTransformer,
    private val instanceReferenceProvider: InstanceReferenceProvider,
    private val reportValueConverter: ReportValueConverter,
    private val clockTestAnalysis: ClockTestAnalysis,
) {
    private class ReportPaths(
        val variables: Map<String, ClassWitnessVariable>,
        val instances: Map<String, Expression>,
    )

    private inner class WitnessContext(
        val reportTrace: ReportTrace,
        val compilation: FlattenedCompilationContext,
        val verdict: VerificationVerdict,
    ) {
        private val classDeclaration = compilation.inlinedOxsts.classDeclaration

        private val paths = reportPaths(compilation)

        private val clockStates = reportTrace.states.map {
            clockValues(it)
        }

        private val hasClocks = reportTrace.variables.any {
            paths.variables[it.variable]?.let { variable ->
                clockTestAnalysis.isClock(variable.declaredVariable)
            } == true
        }

        private val scale = timeScale()

        private val rootFeature by lazy {
            OxstsFactory.createFeatureDeclaration().also {
                it.kind = FeatureKind.CONTAINMENT
                it.typeSpecification = OxstsFactory.createTypeSpecification().also {
                    it.domain = classDeclaration
                }
                it.name = "root"
            }
        }

        private val stateVariable by lazy {
            OxstsFactory.createVariableDeclaration().also {
                it.name = "step"
                it.annotation = OxstsFactory.createAnnotationContainer()
                it.typeSpecification = OxstsFactory.createTypeSpecification().also {
                    it.domain = builtinSymbolResolver.intDatatype(classDeclaration)
                }
                it.expression = OxstsFactory.createLiteralInteger(0)
            }
        }

        private val timeVariable by lazy {
            OxstsFactory.createVariableDeclaration().also {
                it.name = "time"
                it.annotation = OxstsFactory.createAnnotationContainer()
                it.typeSpecification = OxstsFactory.createTypeSpecification().also {
                    it.domain = builtinSymbolResolver.clockDatatype(classDeclaration)
                }
                it.expression = OxstsFactory.createLiteralInteger(0)
            }
        }

        private val originalProperty by lazy {
            redefinitionAwareReferenceResolver.resolveOrNull(rootFeature, "prop") as? PropertyDeclaration ?: error("Verified class has no property.")
        }

        fun createInlinedOxsts(): InlinedOxsts {
            val inlinedOxsts = OxstsFactory.createInlinedOxsts()
            inlinedOxsts.rootFeature = rootFeature
            inlinedOxsts.classDeclaration = classDeclaration
            inlinedOxsts.isWitness = true
            inlinedOxsts.variables += stateVariable
            if (hasClocks) {
                inlinedOxsts.timeScale = scale.intValueExact()
                inlinedOxsts.variables += timeVariable
            }
            inlinedOxsts.initTransition = createInitTransitionDeclaration()
            inlinedOxsts.mainTransition = createMainTransitionDeclaration()
            inlinedOxsts.property = createPropertyDeclaration()
            return inlinedOxsts
        }

        private fun createInitTransitionDeclaration(): TransitionDeclaration {
            val initTransition = builtinSymbolResolver.anythingInitTransition(classDeclaration)
            val transition = redefinitionAwareReferenceResolver.resolve(rootFeature, initTransition) as TransitionDeclaration

            return OxstsFactory.createTransitionDeclaration().also {
                it.kind = TransitionKind.INIT
                it.branches += createStepOperation(0, transition, reportTrace.initial.state, null)
            }
        }

        private fun createMainTransitionDeclaration(): TransitionDeclaration {
            val mainTransition = builtinSymbolResolver.anythingMainTransition(classDeclaration)
            val transition = redefinitionAwareReferenceResolver.resolve(rootFeature, mainTransition) as TransitionDeclaration

            return OxstsFactory.createTransitionDeclaration().also {
                it.kind = TransitionKind.TRAN

                if (reportTrace.steps.isEmpty()) {
                    it.branches += OxstsFactory.createSequenceOperation()
                }

                var time = Fraction.ZERO
                for ((index, step) in reportTrace.steps.withIndex()) {
                    time += step.elapsed
                    it.branches += createStepOperation(index + 1, transition, step.target, time)
                }
            }
        }

        private fun createPropertyDeclaration(): PropertyDeclaration {
            val call = OxstsFactory.createCallSuffixExpression().also {
                it.primary = OxstsFactory.createNavigationSuffixExpression().also {
                    it.primary = OxstsFactory.createElementReference().also {
                        it.element = rootFeature
                    }
                    it.member = originalProperty
                }
            }
            val expression = if (verdict == VerificationVerdict.Failed) {
                OxstsFactory.createNegationOperator(call)
            } else {
                call
            }
            return OxstsFactory.createPropertyDeclaration().also {
                it.expression = expression
            }
        }

        private fun createStepOperation(
            position: Int,
            transition: TransitionDeclaration,
            state: Int,
            time: Fraction?,
        ): SequenceOperation {
            val realTransitionDeclaration = redefinitionAwareReferenceResolver.resolve(rootFeature, transition) as TransitionDeclaration

            return OxstsFactory.createSequenceOperation().also {
                it.steps += createAssumption(OxstsFactory.createElementReference(stateVariable), OxstsFactory.createLiteralInteger(position))

                it.steps += createInlineTransitionCall(realTransitionDeclaration)

                if (time != null && hasClocks) {
                    it.steps += createAssumption(OxstsFactory.createElementReference(timeVariable), scaled(time))
                }

                it.steps += stateAssumptions(reportTrace.states[state])

                it.steps += OxstsFactory.createAssignmentOperation().also { assignment ->
                    assignment.reference = OxstsFactory.createElementReference(stateVariable)
                    assignment.expression = OxstsFactory.createLiteralInteger(position + 1)
                }
            }
        }

        private fun stateAssumptions(state: ReportState): List<AssumptionOperation> {
            return state.values.flatMap { (key, value) ->
                val variable = paths.variables[key] ?: error("the report names the variable $key, which the verified model does not hold")
                if (variable.recordField != null) {
                    recordAssumptions(variable.declaredReference.rooted(), variable.declaredVariable.typeSpecification.domain, value)
                } else if (clockTestAnalysis.isClock(variable.declaredVariable)) {
                    listOf(createAssumption(variable.reference.rooted(), scaled(clockValue(key, value))))
                } else {
                    listOf(createAssumption(variable.reference.rooted(), literal(value, variable.declaredVariable.typeSpecification.domain)))
                }
            }
        }

        private fun recordAssumptions(
            reference: Expression,
            domain: DomainDeclaration,
            value: ReportValue,
        ): List<AssumptionOperation> {
            val record = domain as? RecordDeclaration ?: error("a record value is reported for the type ${domain.name}")
            val fields = value as? ReportValue.Record ?: error("the value of a record is an object of its fields")
            return fields.fields.flatMap { (name, fieldValue) ->
                val field = record.members.filterIsInstance<VariableDeclaration>().singleOrNull {
                    it.name == name
                } ?: error("the record ${record.name} has no field $name")
                val fieldReference = OxstsFactory.createNavigationSuffixExpression().also {
                    it.primary = reference.copy()
                    it.member = field
                }
                if (fieldValue is ReportValue.Record) {
                    recordAssumptions(fieldReference, field.typeSpecification.domain, fieldValue)
                } else {
                    listOf(createAssumption(fieldReference, literal(fieldValue, field.typeSpecification.domain)))
                }
            }
        }

        private fun literal(value: ReportValue, domain: DomainDeclaration): Expression {
            return when (value) {
                is ReportValue.Bool -> OxstsFactory.createLiteralBoolean(value.value)
                is ReportValue.Integer -> OxstsFactory.createLiteralInteger(value.value.intValueExact())
                is ReportValue.Real -> OxstsFactory.createLiteralReal().also {
                    it.value = value.value
                }
                is ReportValue.EnumLiteral -> enumLiteral(domain, value.name)
                is ReportValue.Instance -> instance(value.path)
                is ReportValue.NoInstance -> OxstsFactory.createLiteralNothing()
                is ReportValue.Text -> when {
                    domain is EnumDeclaration -> enumLiteral(domain, value.value)
                    value.value in paths.instances -> instance(value.value)
                    else -> OxstsFactory.createLiteralString().also {
                        it.value = value.value
                    }
                }
                is ReportValue.Rational -> error("a fraction is the value of a clock only")
                is ReportValue.Array -> error("a state holds an array slot by slot")
                is ReportValue.Record -> error("a record value is assumed field by field")
            }
        }

        private fun enumLiteral(domain: DomainDeclaration, name: String): Expression {
            val enumDeclaration = domain as? EnumDeclaration ?: error("the enum literal $name is reported for the type ${domain.name}")
            val literal = enumDeclaration.literals.singleOrNull {
                it.name == name
            } ?: error("the enum ${enumDeclaration.name} has no literal $name")
            return OxstsFactory.createElementReference(literal)
        }

        private fun instance(path: String): Expression {
            val reference = paths.instances[path] ?: error("the report names the instance $path, which the verified model does not hold")
            return reference.rooted()
        }

        private fun clockValues(state: ReportState): List<Fraction> {
            return state.values.mapNotNull { (key, value) ->
                val variable = paths.variables[key] ?: return@mapNotNull null
                if (clockTestAnalysis.isClock(variable.declaredVariable)) {
                    clockValue(key, value)
                } else {
                    null
                }
            }
        }

        private fun clockValue(key: String, value: ReportValue): Fraction {
            return when (value) {
                is ReportValue.Rational -> value.value
                is ReportValue.Integer -> Fraction.of(value.value)
                is ReportValue.Text -> Fraction.parse(value.value)
                else -> error("the clock $key holds no amount of time")
            }
        }

        // the common denominator of every amount the report states, so that each counts a whole number of scaled units
        private fun timeScale(): BigInteger {
            val amounts = reportTrace.steps.map {
                it.elapsed
            } + clockStates.flatten()
            return amounts.fold(BigInteger.ONE) { scale, amount ->
                scale / scale.gcd(amount.denominator) * amount.denominator
            }
        }

        private fun scaled(amount: Fraction): Expression {
            val units = amount.numerator * (scale / amount.denominator)
            return OxstsFactory.createLiteralInteger(units.intValueExact())
        }

        private fun createAssumption(left: Expression, right: Expression): AssumptionOperation {
            return OxstsFactory.createAssumptionOperation().also {
                it.expression = OxstsFactory.createComparisonOperator().also {
                    it.op = ComparisonOp.EQ
                    it.left = left
                    it.right = right
                }
            }
        }

        private fun createInlineTransitionCall(transition: TransitionDeclaration): InlineCall {
            return OxstsFactory.createInlineCall().also {
                it.callExpression = OxstsFactory.createCallSuffixExpression().also {
                    it.primary = OxstsFactory.createNavigationSuffixExpression().also {
                        it.primary = OxstsFactory.createElementReference().also {
                            it.element = rootFeature
                        }
                        it.member = transition
                    }
                }
            }
        }

        private fun Expression.rooted(): Expression {
            val copy = copy()
            if (copy !is ReferenceExpression) {
                return copy
            }

            val innerMost = copy.innerMostElementReference()

            if (OxstsUtils.isElementContextual(innerMost.element)) {
                innerMost.element = rootFeature
            }

            return copy
        }

        private tailrec fun Expression.innerMostElementReference(): ElementReference {
            return when (this) {
                is ElementReference -> this
                is PostfixUnaryExpression -> primary.innerMostElementReference()
                else -> error("Unsupported expression!")
            }
        }

    }

    private fun reportPaths(compilation: FlattenedCompilationContext): ReportPaths {
        val variables = linkedMapOf<String, ClassWitnessVariable>()
        for (variable in compilation.flatteningInfo.variableHolders.keys) {
            val located = classWitnessTransformer.locate(variable, compilation)
            val reference = if (located.recordField != null) {
                located.declaredReference
            } else {
                located.reference
            }
            variables.putIfAbsent(reportValueConverter.path(reference), located)
        }
        val instances = compilation.rootInstance.treeSequence().drop(1).associate {
            val reference = instanceReferenceProvider.getReference(it)
            reportValueConverter.path(reference) to reference
        }
        return ReportPaths(variables, instances)
    }

    fun createWitnessInlinedOxsts(
        reportTrace: ReportTrace,
        compilation: FlattenedCompilationContext,
        verdict: VerificationVerdict,
    ): InlinedOxsts {
        val context = WitnessContext(reportTrace, compilation, verdict)
        return context.createInlinedOxsts()
    }

}
