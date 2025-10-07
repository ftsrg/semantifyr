/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.inliner

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionKind
import hu.bme.mit.semantifyr.semantics.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.expression.RedefinitionAwareReferenceResolver
import hu.bme.mit.semantifyr.semantics.expression.StaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.expression.evaluateTyped
import hu.bme.mit.semantifyr.semantics.optimization.InlinedOxstsOperationOptimizer
import hu.bme.mit.semantifyr.semantics.optimization.XstsExpressionOptimizer
import hu.bme.mit.semantifyr.semantics.transformation.serializer.CompilationArtifactSaver
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory

@Singleton
class OxstsInliner {

    @Inject
    lateinit var builtinSymbolResolver: BuiltinSymbolResolver

    @Inject
    lateinit var metaStaticExpressionEvaluatorProvider: MetaStaticExpressionEvaluatorProvider

    @Inject
    lateinit var staticExpressionEvaluatorProvider: StaticExpressionEvaluatorProvider

    @Inject
    private lateinit var operationInliner: OperationInliner

    @Inject
    private lateinit var callExpressionInliner: ExpressionInliner

    @Inject
    private lateinit var inlinedOxstsOperationOptimizer: InlinedOxstsOperationOptimizer

    @Inject
    private lateinit var xstsExpressionOptimizer: XstsExpressionOptimizer

    @Inject
    private lateinit var redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver

    @Inject
    private lateinit var compilationArtifactSaver: CompilationArtifactSaver

    fun inlineOxsts(inlinedOxsts: InlinedOxsts) {
        initializeInlining(inlinedOxsts)

        compilationArtifactSaver.commitModelState()

        operationInliner.inlineOperations(inlinedOxsts.rootInstance, inlinedOxsts.initTransition)
        operationInliner.inlineOperations(inlinedOxsts.rootInstance, inlinedOxsts.mainTransition)

        callExpressionInliner.inlineExpressions(inlinedOxsts.rootInstance, inlinedOxsts.initTransition)
        callExpressionInliner.inlineExpressions(inlinedOxsts.rootInstance, inlinedOxsts.mainTransition)
        callExpressionInliner.inlineExpressions(inlinedOxsts.rootInstance, inlinedOxsts.property)

        inlinedOxstsOperationOptimizer.optimize(inlinedOxsts.initTransition)
        inlinedOxstsOperationOptimizer.optimize(inlinedOxsts.mainTransition)
        xstsExpressionOptimizer.optimize(inlinedOxsts.property)
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
