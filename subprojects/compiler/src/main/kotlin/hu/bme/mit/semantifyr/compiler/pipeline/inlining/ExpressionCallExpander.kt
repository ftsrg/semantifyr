/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceReferenceProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.RedefinitionAwareReferenceResolver
import hu.bme.mit.semantifyr.compiler.pipeline.expression.StaticExpressionEvaluationTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.expression.StaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.compiler.pipeline.utils.sourceError
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration

/**
 * Expands expression-level calls (inlined property invocations like
 * `foo.bar(args)`) into their body expressions, and rewrites feature-valued
 * references to their static equivalents.
 *
 * Stateless w.r.t. walking: the orchestrator hands over a single
 * [CallSuffixExpression] or feature reference, the expander returns the
 * rewritten [Expression]. Handles:
 *  - [CallSuffixExpression] to a [PropertyDeclaration]: resolves the target
 *    instance, clones the property's expression body, rewrites self /
 *    argument references.
 *  - Feature-valued reference expressions: evaluates to an instance or
 *    instance set, transforms back to the rewritten expression form.
 */
class ExpressionCallExpander @Inject constructor(
    private val staticExpressionEvaluatorProvider: StaticExpressionEvaluatorProvider,
    private val metaStaticExpressionEvaluatorProvider: MetaStaticExpressionEvaluatorProvider,
    private val expressionRewriter: ExpressionRewriter,
    private val instanceReferenceProvider: InstanceReferenceProvider,
    private val staticEvaluationTransformer: StaticExpressionEvaluationTransformer,
    private val callTargetResolver: CallTargetResolver,
    private val redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver,
) {

    /**
     * Expand a property call to the inlined expression body with self / args
     * substituted.
     */
    fun expandCall(callExpression: CallSuffixExpression, instance: Instance): Expression {
        val target = callTargetResolver.resolve(callExpression, instance)
        val (containerInstance, baseTargetDeclaration) = when (target) {
            is CallTarget.DirectInstance -> target.containerInstance to target.targetDeclaration
            is CallTarget.VariableDispatch -> sourceError(
                callExpression,
                "Variable dispatching in property expressions is not yet implemented (would require if-expression support).",
            )
            is CallTarget.MissingOptional -> sourceError(
                callExpression,
                "Container instance of the property is empty!",
            )
        }

        val actualContainerInstanceReference = instanceReferenceProvider.getReference(containerInstance)

        val property = redefinitionAwareReferenceResolver.resolve(
            containerInstance.domain,
            baseTargetDeclaration,
        ) as? PropertyDeclaration ?: sourceError(
            callExpression,
            "Expected a property declaration at call site, got ${baseTargetDeclaration::class.simpleName}",
        )

        if (property.isAbstract) {
            sourceError(property, "Abstract property can not be inlined!")
        }

        // This trick ensures that the expression rewriter can rewrite the passed-in expression itself as well.
        val expressionHolder = OxstsFactory.createArgument()
        expressionHolder.expression = property.expression.copy()

        expressionRewriter.rewriteExpressionsToContext(expressionHolder.expression, actualContainerInstanceReference)
        expressionRewriter.rewriteExpressionsToCall(expressionHolder.expression, property, callExpression)

        return expressionHolder.expression
    }

    /**
     * If [expression] is a reference that evaluates to a feature, return the
     * transformed expression (typically an instance-id literal form). Returns
     * `null` if the expression is not feature-valued, so the caller can skip
     * rewriting.
     */
    fun expandFeatureReferenceOrNull(expression: Expression, instance: Instance): Expression? {
        if (metaStaticExpressionEvaluatorProvider.evaluate(instance, expression) !is FeatureDeclaration) {
            return null
        }
        val evaluator = staticExpressionEvaluatorProvider.getEvaluator(instance)
        val evaluation = evaluator.evaluate(expression)
        return staticEvaluationTransformer.transformEvaluation(evaluation)
    }

}
