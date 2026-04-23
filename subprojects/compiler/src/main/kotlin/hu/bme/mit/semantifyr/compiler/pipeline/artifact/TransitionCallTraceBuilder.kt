/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.artifact

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.compiler.pipeline.context.TransitionCallTraceMap
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceEvaluation
import hu.bme.mit.semantifyr.compiler.pipeline.expression.StaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.tryEvaluateTypedOrNull
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParameterDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration

class ArgumentTrace(
    val parameterDeclaration: ParameterDeclaration?,
    val evaluation: ExpressionEvaluation?,
)

class TransitionCallTrace(
    val self: InstanceEvaluation,
    val transitionDeclaration: TransitionDeclaration,
    val arguments: List<ArgumentTrace>,
)

@Singleton
class TransitionCallTraceBuilder @Inject constructor(
    private val staticExpressionEvaluatorProvider: StaticExpressionEvaluatorProvider,
) {

    private var tracerCount = 0

    private val transitionCallTraceMap = mutableMapOf<String, TransitionCallTrace>()

    // TODO: store call-stack data as well to allow structural trace information
    fun traceTransitionCall(instance: Instance, containerInstance: Instance, transitionDeclaration: TransitionDeclaration, callExpression: CallSuffixExpression): Operation {
        val transitionTraceOperation = OxstsFactory.createTraceOperation().also {
            it.name = "__transition_tracer${tracerCount++}"
        }

        val evaluator = staticExpressionEvaluatorProvider.getEvaluator(instance)

        val transitionCallTrace = TransitionCallTrace(
            InstanceEvaluation(containerInstance),
            transitionDeclaration,
            callExpression.arguments.map {
                ArgumentTrace(it.parameter, evaluator.tryEvaluateTypedOrNull(ExpressionEvaluation::class.java, it.expression))
            },
        )
        transitionCallTraceMap[transitionTraceOperation.name] = transitionCallTrace

        return transitionTraceOperation
    }

    fun build(): TransitionCallTraceMap {
        return TransitionCallTraceMap(transitionCallTraceMap.toMap())
    }

}
