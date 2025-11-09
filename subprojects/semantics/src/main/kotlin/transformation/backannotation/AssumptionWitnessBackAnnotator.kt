/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.backannotation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureKind
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineCall
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PostfixUnaryExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionKind
import hu.bme.mit.semantifyr.semantics.expression.RedefinitionAwareReferenceResolver
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
import hu.bme.mit.semantifyr.semantics.utils.copy
import org.eclipse.emf.common.util.URI

@CompilationScoped
class AssumptionWitnessBackAnnotator {

    @Inject
    private lateinit var builtinSymbolResolver: BuiltinSymbolResolver

    @Inject
    private lateinit var redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver

    private inner class WitnessContext(val witness: OxstsClassAssumptionWitness) {

        val stepValues = mutableMapOf<OxstsClassAssumptionWitnessState, Int>()
        private var lastStepValue = -1
        private val OxstsClassAssumptionWitnessState.stepValue
            get() = stepValues.getOrPut(this) {
                lastStepValue++
            }

        private val rootFeature by lazy {
            OxstsFactory.createFeatureDeclaration().also {
                it.kind = FeatureKind.CONTAINMENT
                it.type = witness.inlinedOxsts.classDeclaration
                it.name = "root"
            }
        }

        private val stateVariable by lazy {
            OxstsFactory.createVariableDeclaration().also {
                it.name = "step"
                it.annotation = OxstsFactory.createAnnotationContainer()
                it.type = builtinSymbolResolver.intDatatype(witness.inlinedOxsts.classDeclaration)
                it.expression = OxstsFactory.createLiteralInteger(-1)
            }
        }

        fun createInlinedOxsts(): InlinedOxsts {
            val resourceSet = witness.inlinedOxsts.eResource().resourceSet
            val path = witness.inlinedOxsts.eResource().uri.toString().replace("inlined.oxsts", "witness.oxsts")
            val uri = URI.createURI(path)

            val inlinedOxsts = OxstsFactory.createInlinedOxsts()

            resourceSet.getResource(uri, false)?.delete(mutableMapOf<Any, Any>())
            resourceSet.createResource(uri).contents += inlinedOxsts

            initializeInlinedOxstsModel(inlinedOxsts)

            return inlinedOxsts
        }

        private fun initializeInlinedOxstsModel(inlinedOxstsWitness: InlinedOxsts) {
            inlinedOxstsWitness.rootFeature = rootFeature
            inlinedOxstsWitness.classDeclaration = witness.inlinedOxsts.classDeclaration
            inlinedOxstsWitness.isWitness = true
            inlinedOxstsWitness.variables += stateVariable
            inlinedOxstsWitness.initTransition = createInitTransitionDeclaration()
            inlinedOxstsWitness.mainTransition = createMainTransitionDeclaration()
            inlinedOxstsWitness.property = createPropertyDeclaration()
        }

        private fun createInitTransitionDeclaration(): TransitionDeclaration {
            val initTransition = builtinSymbolResolver.anythingInitTransition(witness.inlinedOxsts.classDeclaration)
            val transition = redefinitionAwareReferenceResolver.resolve(rootFeature, initTransition) as TransitionDeclaration

            return OxstsFactory.createTransitionDeclaration().also {
                it.kind = TransitionKind.INIT
                it.branches += OxstsFactory.createSequenceOperation().also {
                    it.steps += createStateOperation(witness.initialState, transition).steps
                    it.steps += createStateOperation(witness.initializedState, transition).steps
                }
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
            val property = redefinitionAwareReferenceResolver.resolveOrNull(rootFeature, "prop") as? PropertyDeclaration

            if (property == null) {
                return OxstsFactory.createPropertyDeclaration().also {
                    it.expression = OxstsFactory.createLiteralBoolean(true)
                }
            }

            return OxstsFactory.createPropertyDeclaration().also {
                it.expression = OxstsFactory.createCallSuffixExpression().also {
                    it.primary = OxstsFactory.createNavigationSuffixExpression().also {
                        it.primary = OxstsFactory.createElementReference().also {
                            it.element = rootFeature
                        }
                        it.member = property
                    }
                }
            }
        }

        private fun createStateOperation(state: OxstsClassAssumptionWitnessState, transition: TransitionDeclaration): SequenceOperation {
            val realTransitionDeclaration = redefinitionAwareReferenceResolver.resolve(rootFeature, transition) as TransitionDeclaration

            return OxstsFactory.createSequenceOperation().also {
                it.steps += createStateVariableAssumption(state.stepValue)

                it.steps += createInlineTransitionCall(realTransitionDeclaration)

                it.steps += state.toAssumptionOperations()

                if (witness.getNextStates(state).any()) {
                    it.steps += createNextStateTransitionOperation(state)
                }
            }
        }

        private fun createNextStateTransitionOperation(state: OxstsClassAssumptionWitnessState): ChoiceOperation {
            return OxstsFactory.createChoiceOperation().also {
                for (nextState in witness.getNextStates(state)) {
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

            if (! OxstsUtils.isGlobalFeature(innerMost.element)) {
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

    fun createWitnessInlinedOxsts(witness: OxstsClassAssumptionWitness): InlinedOxsts {
        val context = WitnessContext(witness)
        return context.createInlinedOxsts()
    }

}
