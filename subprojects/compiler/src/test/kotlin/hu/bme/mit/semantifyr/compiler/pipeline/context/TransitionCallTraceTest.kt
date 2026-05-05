/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.context

import hu.bme.mit.semantifyr.compiler.pipeline.inlining.TransitionCallTrace
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class TransitionCallTraceTest {

    @Test
    fun `getTransitionCallTrace returns the trace stored under the tracer variable`() {
        val trace: TransitionCallTrace = mock()
        val variable = newTracerVariable("__transition_tracer0")
        val map = TransitionCallTraceMap(mapOf(variable to trace))

        assertThat(map.getTransitionCallTrace(variable)).isSameAs(trace)
    }

    @Test
    fun `getTransitionCallTrace throws when the tracer variable is unknown`() {
        val map = TransitionCallTraceMap(emptyMap())
        val variable = newTracerVariable("missing")

        assertThatThrownBy {
            map.getTransitionCallTrace(variable)
        }.isInstanceOf(IllegalStateException::class.java).hasMessageContaining("missing")
    }

    @Test
    fun `empty map throws for any lookup`() {
        val map = TransitionCallTraceMap(emptyMap())
        val variable = newTracerVariable("any")

        assertThatThrownBy {
            map.getTransitionCallTrace(variable)
        }.isInstanceOf(IllegalStateException::class.java)
    }

    private fun newTracerVariable(variableName: String): VariableDeclaration {
        return OxstsFactory.createVariableDeclaration().also {
            it.name = variableName
        }
    }
}
