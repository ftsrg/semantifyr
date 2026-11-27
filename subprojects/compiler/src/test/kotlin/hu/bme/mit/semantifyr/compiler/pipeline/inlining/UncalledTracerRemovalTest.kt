/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.compiler.pipeline.expression.ExpressionEvaluationSerializer
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.TestModels.testModel
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UncalledTracerRemovalTest : InliningTestBase() {

    @Test
    fun `a traced call whose body cannot execute gets no tracer`() {
        prepare("Machine", MODEL) {
            val inlined = inlineAll(it)
            val serializer = it.compilationInjector.getInstance(ExpressionEvaluationSerializer::class.java)
            val tracers = inlined.inlinedOxsts.variables.filter { variable ->
                TransitionTracerNames.isTracerName(variable.name)
            }
            val traced = tracers.map { variable ->
                val trace = inlined.transitionCallTraces.getTransitionCallTrace(variable)
                "${serializer.serialize(trace.self)}.${trace.transitionDeclaration.name}"
            }
            val referencedTracers = inlined.inlinedOxsts.eAllOfType<ElementReference>().mapNotNull { reference ->
                reference.element as? VariableDeclaration
            }.filter { variable ->
                TransitionTracerNames.isTracerName(variable.name)
            }.toSet()

            assertThat(traced).containsExactlyInAnyOrder(
                "root.step.fire",
                "root.step.port.send",
            )
            assertThat(referencedTracers).containsExactlyInAnyOrderElementsOf(tracers)
        }
    }

    companion object {
        private val MODEL = testModel(UncalledTracerRemovalTest::class.java, "model.semantifyr")
    }

}
