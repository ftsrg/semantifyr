/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.tracer

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParameterDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TraceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.semantics.expression.InstanceEvaluation
import hu.bme.mit.semantifyr.semantics.expression.StaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.expression.tryEvaluateTypedOrNull
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory

class ArgumentTrace(
    val parameterDeclaration: ParameterDeclaration?,
    val evaluation: ExpressionEvaluation?,
)

class TransitionCallTrace(
    val self: InstanceEvaluation,
    val transitionDeclaration: TransitionDeclaration,
    val arguments: List<ArgumentTrace>,
)

@CompilationScoped
class TransitionCallTracer {

    @Inject
    private lateinit var staticExpressionEvaluatorProvider: StaticExpressionEvaluatorProvider

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

    fun getTransitionCallTrace(traceOperation: TraceOperation): TransitionCallTrace {
        // FIXME: should not use the name here.
        // TODO: should be replaced with generic tracer, i.e., trace (self: self, arg1: "some value", arg2: a.b.cd)
        return transitionCallTraceMap[traceOperation.name] ?: error("No transition call trace was found!")
    }

}
