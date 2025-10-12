/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.inliner

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionKind
import hu.bme.mit.semantifyr.semantics.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.expression.RedefinitionAwareReferenceResolver
import hu.bme.mit.semantifyr.semantics.expression.StaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.expression.evaluateTyped
import hu.bme.mit.semantifyr.semantics.optimization.InlinedOxstsOperationOptimizer
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.transformation.serializer.CompilationStateManager
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory

@CompilationScoped
class OxstsInliner {

    @Inject
    private lateinit var builtinSymbolResolver: BuiltinSymbolResolver

    @Inject
    private lateinit var metaStaticExpressionEvaluatorProvider: MetaStaticExpressionEvaluatorProvider

    @Inject
    private lateinit var staticExpressionEvaluatorProvider: StaticExpressionEvaluatorProvider

    @Inject
    private lateinit var inlinedOxstsOperationOptimizer: InlinedOxstsOperationOptimizer

    @Inject
    private lateinit var redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver

    @Inject
    private lateinit var compilationStateManager: CompilationStateManager

    @Inject
    private lateinit var operationCallInlinerProvider: Provider<OperationCallInliner>

    @Inject
    private lateinit var expressionCallInlinerProvider: Provider<ExpressionCallInliner>

    fun inlineOxsts(inlinedOxsts: InlinedOxsts) {
        initializeInlining(inlinedOxsts)

        compilationStateManager.commitModelState()

        inlineOperationCalls(inlinedOxsts.rootInstance, inlinedOxsts.initTransition)
        inlineOperationCalls(inlinedOxsts.rootInstance, inlinedOxsts.mainTransition)
        inlineExpressionCalls(inlinedOxsts.rootInstance, inlinedOxsts.property)

        inlinedOxstsOperationOptimizer.optimize(inlinedOxsts)
    }

    private fun inlineOperationCalls(rootInstance: Instance, transition: TransitionDeclaration) {
        val processor = operationCallInlinerProvider.get()
        processor.instance = rootInstance

        for (branch in transition.branches) {
            processor.process(branch)
        }
    }

    private fun inlineExpressionCalls(rootInstance: Instance, propertyDeclaration: PropertyDeclaration) {
        val processor = expressionCallInlinerProvider.get()
        processor.instance = rootInstance
        processor.process(propertyDeclaration.expression)
    }

    private fun initializeInlining(inlinedOxsts: InlinedOxsts) {
        val builtinInit = OxstsFactory.createNavigationSuffixExpression().also {
            it.primary = OxstsFactory.createElementReference().also {
                it.element = inlinedOxsts.rootFeature
            }
            it.member = builtinSymbolResolver.anythingInitTransition(inlinedOxsts)
        }
        val builtinMain = OxstsFactory.createNavigationSuffixExpression().also {
            it.primary = OxstsFactory.createElementReference().also {
                it.element = inlinedOxsts.rootFeature
            }
            it.member = builtinSymbolResolver.anythingMainTransition(inlinedOxsts)
        }

        inlinedOxsts.initTransition = createTransitionDeclaration(inlinedOxsts, TransitionKind.INIT, builtinInit)
        inlinedOxsts.mainTransition = createTransitionDeclaration(inlinedOxsts, TransitionKind.TRAN, builtinMain)
        inlinedOxsts.property = createPropertyDeclaration(inlinedOxsts)
    }

    private fun createTransitionDeclaration(inlinedOxsts: InlinedOxsts, transitionKind: TransitionKind, expression: Expression): TransitionDeclaration {
        val metaEvaluator = metaStaticExpressionEvaluatorProvider.getEvaluator(inlinedOxsts.rootInstance)
        val transition = metaEvaluator.evaluateTyped(TransitionDeclaration::class.java, expression)

        return OxstsFactory.createTransitionDeclaration().also {
            it.kind = transitionKind
            it.branches += OxstsFactory.createSequenceOperation().also {
                it.steps += OxstsFactory.createInlineCall().also {
                    it.callExpression = OxstsFactory.createCallSuffixExpression().also {
                        it.primary = OxstsFactory.createNavigationSuffixExpression().also {
                            it.primary = OxstsFactory.createElementReference().also {
                                it.element = inlinedOxsts.rootFeature
                            }
                            it.member = transition
                        }
                    }
                }
            }
        }
    }

    private fun createPropertyDeclaration(inlinedOxsts: InlinedOxsts): PropertyDeclaration {
        val evaluator = staticExpressionEvaluatorProvider.getEvaluator(inlinedOxsts.rootInstance)
        val rootFeatureInstance = evaluator.evaluateSingleInstance(OxstsFactory.createElementReference(inlinedOxsts.rootFeature))
        val property = redefinitionAwareReferenceResolver.resolveOrNull(rootFeatureInstance, "prop") as? PropertyDeclaration

        if (property == null) {
            return OxstsFactory.createPropertyDeclaration().also {
                it.expression = OxstsFactory.createLiteralBoolean(true)
            }
        }

        return OxstsFactory.createPropertyDeclaration().also {
            it.expression = OxstsFactory.createCallSuffixExpression().also {
                it.primary = OxstsFactory.createNavigationSuffixExpression().also {
                    it.primary = OxstsFactory.createElementReference().also {
                        it.element = inlinedOxsts.rootFeature
                    }
                    it.member = property
                }
            }
        }
    }

}
