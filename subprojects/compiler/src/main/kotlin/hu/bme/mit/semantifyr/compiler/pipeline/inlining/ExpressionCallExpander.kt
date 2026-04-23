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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration

class ExpressionCallExpander @Inject constructor(
    private val staticExpressionEvaluatorProvider: StaticExpressionEvaluatorProvider,
    private val metaStaticExpressionEvaluatorProvider: MetaStaticExpressionEvaluatorProvider,
    private val expressionRewriter: ExpressionRewriter,
    private val instanceReferenceProvider: InstanceReferenceProvider,
    private val staticEvaluationTransformer: StaticExpressionEvaluationTransformer,
    private val callTargetResolver: CallTargetResolver,
    private val redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver,
) {

    fun expandCall(callExpression: CallSuffixExpression, instance: Instance): Expression {
        return when (val target = callTargetResolver.resolve(callExpression, instance)) {
            is CallTarget.DirectInstance -> expandForContainer(
                containerInstance = target.containerInstance,
                baseTargetDeclaration = target.targetDeclaration,
                callExpression = callExpression,
            )
            is CallTarget.VariableDispatch -> dispatchOverVariable(
                target = target,
                callExpression = callExpression,
            )
            is CallTarget.MissingOptional -> sourceError(
                callExpression,
                "Container instance of the property is empty!",
            )
        }
    }

    private fun expandForContainer(
        containerInstance: Instance,
        baseTargetDeclaration: NamedElement,
        callExpression: CallSuffixExpression,
    ): Expression {
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

    private fun dispatchOverVariable(
        target: CallTarget.VariableDispatch,
        callExpression: CallSuffixExpression,
    ): Expression {
        if (target.candidateInstances.isEmpty()) {
            sourceError(
                callExpression,
                "Variable '${target.variable.name}' has no candidate instances to dispatch the property call over.",
            )
        }

        val candidates = target.candidateInstances.toList()

        // Start with the last candidate's expansion as the final else branch.
        // The instance tree invariant guarantees `var` holds one of these
        // candidates, so the fallthrough is safe.
        var result = expandForContainer(
            containerInstance = candidates.last(),
            baseTargetDeclaration = target.targetDeclaration,
            callExpression = callExpression,
        )

        for (index in candidates.size - 2 downTo 0) {
            val candidate = candidates[index]
            val candidateReference = instanceReferenceProvider.getReference(candidate)
            val thenExpr = expandForContainer(
                containerInstance = candidate,
                baseTargetDeclaration = target.targetDeclaration,
                callExpression = callExpression,
            )
            val guard = OxstsFactory.createComparisonOperator().also {
                it.op = ComparisonOp.EQ
                it.left = target.containerReferenceExpression.copy()
                it.right = candidateReference
            }
            result = OxstsFactory.createIfThenElse().also {
                it.guard = guard
                it.then = thenExpr
                it.`else` = result
            }
        }
        return result
    }

    fun expandFeatureReferenceOrNull(expression: Expression, instance: Instance): Expression? {
        if (metaStaticExpressionEvaluatorProvider.evaluate(instance, expression) !is FeatureDeclaration) {
            return null
        }
        val evaluator = staticExpressionEvaluatorProvider.getEvaluator(instance)
        val evaluation = evaluator.evaluate(expression)
        return staticEvaluationTransformer.transformEvaluation(evaluation)
    }

}
