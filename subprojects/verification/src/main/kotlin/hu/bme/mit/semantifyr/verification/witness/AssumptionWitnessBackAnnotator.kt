/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.witness

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.compiler.pipeline.expression.RedefinitionAwareReferenceResolver
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureKind
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineCall
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PostfixUnaryExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionKind

class AssumptionWitnessBackAnnotator @Inject constructor(
    private val builtinSymbolResolver: BuiltinSymbolResolver,
    private val redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver,
) {
    private inner class WitnessContext(
        val witness: OxstsClassAssumptionWitness,
        val verdict: VerificationVerdict,
    ) {
        val stepValues = mutableMapOf<OxstsClassAssumptionWitnessState, Int>()
        private var lastStepValue = -1
        private val OxstsClassAssumptionWitnessState.stepValue
            get() = stepValues.getOrPut(this) {
                lastStepValue++
            }

        private val rootFeature by lazy {
            OxstsFactory.createFeatureDeclaration().also {
                it.kind = FeatureKind.CONTAINMENT
                it.typeSpecification = OxstsFactory.createTypeSpecification().also {
                    it.domain = witness.inlinedOxsts.classDeclaration
                }
                it.name = "root"
            }
        }

        private val stateVariable by lazy {
            OxstsFactory.createVariableDeclaration().also {
                it.name = "step"
                it.annotation = OxstsFactory.createAnnotationContainer()
                it.typeSpecification = OxstsFactory.createTypeSpecification().also {
                    it.domain = builtinSymbolResolver.intDatatype(witness.inlinedOxsts.classDeclaration)
                }
                it.expression = OxstsFactory.createLiteralInteger(-1)
            }
        }

        private val originalProperty by lazy {
            redefinitionAwareReferenceResolver.resolveOrNull(rootFeature, "prop") as? PropertyDeclaration ?: error("Verified class has no property.")
        }

        fun createInlinedOxsts(): InlinedOxsts {
            val inlinedOxsts = OxstsFactory.createInlinedOxsts()
            inlinedOxsts.rootFeature = rootFeature
            inlinedOxsts.classDeclaration = witness.inlinedOxsts.classDeclaration
            inlinedOxsts.isWitness = true
            inlinedOxsts.variables += stateVariable
            inlinedOxsts.initTransition = createInitTransitionDeclaration()
            inlinedOxsts.mainTransition = createMainTransitionDeclaration()
            inlinedOxsts.property = createPropertyDeclaration()
            return inlinedOxsts
        }

        private fun createInitTransitionDeclaration(): TransitionDeclaration {
            val initTransition = builtinSymbolResolver.anythingInitTransition(witness.inlinedOxsts.classDeclaration)
            val transition = redefinitionAwareReferenceResolver.resolve(rootFeature, initTransition) as TransitionDeclaration

            return OxstsFactory.createTransitionDeclaration().also {
                it.kind = TransitionKind.INIT
                it.branches += OxstsFactory.createSequenceOperation().also {
                    it.steps += createInitialStateOperation(witness.initialState).steps
                    if (witness.initializedState != null) {
                        it.steps += createStateOperation(witness.initializedState, transition).steps
                    }
                }
            }
        }

        private fun createInitialStateOperation(state: OxstsClassAssumptionWitnessState): SequenceOperation {
            return OxstsFactory.createSequenceOperation().also {
                it.steps += createStateVariableAssumption(state.stepValue)
                it.steps += state.toAssumptionOperations()
                it.steps += createStateVariableAssignment(state.stepValue + 1)
            }
        }

        private fun createMainTransitionDeclaration(): TransitionDeclaration {
            val mainTransition = builtinSymbolResolver.anythingMainTransition(witness.inlinedOxsts.classDeclaration)
            val transition = redefinitionAwareReferenceResolver.resolve(rootFeature, mainTransition) as TransitionDeclaration

            return OxstsFactory.createTransitionDeclaration().also {
                it.kind = TransitionKind.TRAN

                if (witness.transitionStates.isEmpty()) {
                    it.branches += OxstsFactory.createSequenceOperation()
                }

                for (state in witness.transitionStates) {
                    it.branches += createStateOperation(state, transition)
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

        private fun createStateOperation(
            state: OxstsClassAssumptionWitnessState,
            transition: TransitionDeclaration,
        ): SequenceOperation {
            val realTransitionDeclaration = redefinitionAwareReferenceResolver.resolve(rootFeature, transition) as TransitionDeclaration

            return OxstsFactory.createSequenceOperation().also {
                it.steps += createStateVariableAssumption(state.stepValue)

                it.steps += createInlineTransitionCall(realTransitionDeclaration)

                it.steps += state.toAssumptionOperations()

                if (witness.getNextStates(state).size > 1) {
                    it.steps += createNextStateTransitionOperation(state)
                } else {
                    it.steps += createStateVariableAssignment(state.stepValue + 1)
                }
            }
        }

        private fun createNextStateTransitionOperation(state: OxstsClassAssumptionWitnessState): Operation {
            val nextStates = witness.getNextStates(state)

            if (nextStates.size == 1) {
                return createStateVariableAssignment(nextStates.single().stepValue)
            }

            return OxstsFactory.createChoiceOperation().also {
                for (nextState in nextStates) {
                    it.branches += OxstsFactory.createSequenceOperation().also {
                        it.steps += createStateVariableAssignment(nextState.stepValue)
                    }
                }
            }
        }

        private fun createStateVariableAssumption(value: Int): AssumptionOperation {
            return OxstsFactory.createAssumptionOperation().also {
                it.expression = OxstsFactory.createComparisonOperator().also {
                    it.op = ComparisonOp.EQ
                    it.left = OxstsFactory.createElementReference(stateVariable)
                    it.right = OxstsFactory.createLiteralInteger(value)
                }
            }
        }

        private fun createStateVariableAssignment(value: Int): AssignmentOperation {
            return OxstsFactory.createAssignmentOperation().also {
                it.reference = OxstsFactory.createElementReference(stateVariable)
                it.expression = OxstsFactory.createLiteralInteger(value)
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

        private fun OxstsClassAssumptionWitnessStateValue.toAssumption(): AssumptionOperation {
            return OxstsFactory.createAssumptionOperation().also {
                it.expression = OxstsFactory.createComparisonOperator().also {
                    it.op = ComparisonOp.EQ
                    it.left = variableReference.copy().fixRootFeatureExpression(rootFeature)
                    it.right = value.copy().fixRootFeatureExpression(rootFeature)
                }
            }
        }

        private fun Expression.fixRootFeatureExpression(actualRoot: FeatureDeclaration): Expression {
            if (this !is ReferenceExpression) {
                return this
            }

            val innerMost = innerMostElementReference()

            if (OxstsUtils.isElementContextual(innerMost.element)) {
                innerMost.element = actualRoot
            }

            return this
        }

        private tailrec fun Expression.innerMostElementReference(): ElementReference {
            return when (this) {
                is ElementReference -> this
                is PostfixUnaryExpression -> primary.innerMostElementReference()
                else -> error("Unsupported expression!")
            }
        }

        private fun OxstsClassAssumptionWitnessState.toAssumptionOperations(): List<AssumptionOperation> {
            return values.map {
                it.toAssumption()
            }
        }

    }

    fun createWitnessInlinedOxsts(
        witness: OxstsClassAssumptionWitness,
        verdict: VerificationVerdict,
    ): InlinedOxsts {
        val context = WitnessContext(witness, verdict)
        return context.createInlinedOxsts()
    }

}
