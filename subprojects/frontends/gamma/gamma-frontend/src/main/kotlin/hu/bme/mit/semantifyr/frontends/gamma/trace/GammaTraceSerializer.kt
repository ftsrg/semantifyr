/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.trace

import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Act
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ActiveStateAssertion
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Assertion
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ElementReferenceExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventTrigger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ExecutionTrace
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Expression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.FiredTransitionAssertion
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.LiteralBoolean
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.LiteralInteger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.NavigationSuffixExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RaiseEventAct
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RaisedEventAssertion
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ResetAct
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ScheduleAct
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Step
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TimeElapseAct
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TimeoutTrigger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Trigger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.UnaryOp
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VariableValueAssertion
import hu.bme.mit.semantifyr.utils.text.IndentingStringBuilder
import hu.bme.mit.semantifyr.utils.text.buildIndented
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.util.SimpleAttributeResolver

class GammaTraceSerializer {

    fun serialize(gammaPackage: GammaModelPackage) = buildIndented {
        appendLine("package ${gammaPackage.name}")
        for (executionTrace in gammaPackage.traces) {
            appendLine()
            serialize(executionTrace)
        }
    }

    private fun IndentingStringBuilder.serialize(executionTrace: ExecutionTrace) {
        appendIndent("trace ${executionTrace.name} of ${executionTrace.verificationCase.name}") {
            for (step in executionTrace.steps) {
                serialize(step)
            }
        }
    }

    private fun IndentingStringBuilder.serialize(step: Step) {
        appendIndent("step") {
            if (step.acts.isNotEmpty()) {
                appendIndent("act") {
                    for (act in step.acts) {
                        appendLine(serialize(act))
                    }
                }
            }
            appendIndent("assert") {
                for (assertion in step.asserts) {
                    appendLine(serialize(assertion))
                }
            }
        }
    }

    private fun serialize(act: Act): String {
        return when (act) {
            is ResetAct -> "reset"
            is ScheduleAct -> "schedule component"
            is TimeElapseAct -> "elapse ${act.time} s"
            is RaiseEventAct -> "raise ${serializePath(act.event)}"
            else -> error("Unknown act: $act")
        }
    }

    private fun serialize(assertion: Assertion): String {
        return when (assertion) {
            is FiredTransitionAssertion -> {
                val trigger = assertion.trigger?.let {
                    " when ${serialize(it)}"
                } ?: ""
                "transition ${serializePath(assertion.from)} to ${nameOf(assertion.to)}$trigger"
            }
            is RaisedEventAssertion -> "raise ${serializePath(assertion.event)}"
            is ActiveStateAssertion -> "state ${serializePath(assertion.state)}"
            is VariableValueAssertion -> "variable ${serializePath(assertion.variable)} == ${serializeValue(assertion.value)}"
            else -> error("Unknown assertion: $assertion")
        }
    }

    private fun serialize(trigger: Trigger): String {
        return when (trigger) {
            is TimeoutTrigger -> "timeout ${nameOf(trigger.timeout)}"
            is EventTrigger -> "event ${nameOf(trigger.port)}.${nameOf(trigger.event)}"
            else -> error("Unknown trigger: $trigger")
        }
    }

    private fun serializePath(path: Expression): String {
        return when (path) {
            is ElementReferenceExpression -> nameOf(path.element)
            is NavigationSuffixExpression -> "${serializePath(path.primary)}.${nameOf(path.member)}"
            else -> error("Unknown path: $path")
        }
    }

    private fun serializeValue(value: Expression): String {
        return when (value) {
            is LiteralBoolean -> value.isValue.toString()
            is LiteralInteger -> value.value.toString()
            is ArithmeticUnaryOperator if value.op == UnaryOp.MINUS -> "-${serializeValue(value.body)}"
            else -> error("Unknown value: $value")
        }
    }

    private fun nameOf(element: EObject): String {
        return SimpleAttributeResolver.NAME_RESOLVER.apply(element)
    }
}
