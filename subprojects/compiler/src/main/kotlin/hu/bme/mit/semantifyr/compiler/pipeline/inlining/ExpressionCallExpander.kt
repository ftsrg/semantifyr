/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.expression.CompileTimeExpressionEvaluationTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.expression.CompileTimeExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceReferenceProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaCompileTimeExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.RedefinitionAwareReferenceResolver
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.compiler.pipeline.utils.sourceError
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class ExpressionCallExpander @Inject constructor(
    private val compileTimeExpressionEvaluatorProvider: CompileTimeExpressionEvaluatorProvider,
    private val metaCompileTimeExpressionEvaluatorProvider: MetaCompileTimeExpressionEvaluatorProvider,
    private val expressionRewriter: ExpressionRewriter,
    private val instanceReferenceProvider: InstanceReferenceProvider,
    private val staticEvaluationTransformer: CompileTimeExpressionEvaluationTransformer,
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
            sourceError(property, "Property '${NamingUtil.getName(property)}' is abstract and '${containerInstance.domain.name}' does not redefine it")
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
        val branches = target.branches.associate {
            it.candidateInstance to expandForContainer(
                containerInstance = it.containerInstance,
                baseTargetDeclaration = target.targetDeclaration,
                callExpression = callExpression,
            )
        }
        return dispatch(target.containerReferenceExpression, target.variable, branches, callExpression)
    }

    fun isThroughRuntimeHolder(expression: Expression, instance: Instance): Boolean {
        return callTargetResolver.runtimeHolderOf(expression, instance) != null
    }

    fun dispatchMemberReadOrNull(expression: Expression, instance: Instance): Expression? {
        val runtimeHolder = callTargetResolver.runtimeHolderOf(expression, instance) ?: return null
        val branches = runtimeHolder.candidateInstances.associateWith {
            callTargetResolver.replaceHolder(expression, runtimeHolder, instanceReferenceProvider.getReference(it))
        }
        return dispatch(runtimeHolder.holderExpression, runtimeHolder.variable, branches, expression)
    }

    private fun dispatch(
        holderExpression: Expression,
        variable: VariableDeclaration,
        branches: Map<Instance, Expression>,
        dispatchedExpression: Expression,
    ): Expression {
        if (branches.isEmpty()) {
            sourceError(
                dispatchedExpression,
                "Variable '${variable.name}' has no candidate instances to dispatch over.",
            )
        }

        val candidates = branches.keys.toList()

        // The instance tree invariant guarantees the holder holds one of the candidates, so the last one is the final else.
        var result = branches.getValue(candidates.last())

        for (index in candidates.size - 2 downTo 0) {
            val candidate = candidates[index]
            val guard = OxstsFactory.createComparisonOperator().also {
                it.op = ComparisonOp.EQ
                it.left = holderExpression.copy()
                it.right = instanceReferenceProvider.getReference(candidate)
            }
            result = OxstsFactory.createIfThenElse().also {
                it.guard = guard
                it.then = branches.getValue(candidate)
                it.`else` = result
            }
        }
        return result
    }

    fun expandFeatureReferenceOrNull(expression: Expression, instance: Instance): Expression? {
        if (metaCompileTimeExpressionEvaluatorProvider.evaluate(instance, expression) !is FeatureDeclaration) {
            return null
        }
        val evaluator = compileTimeExpressionEvaluatorProvider.getEvaluator(instance)
        val evaluation = evaluator.evaluate(expression)
        return staticEvaluationTransformer.transformEvaluation(evaluation)
    }

}
