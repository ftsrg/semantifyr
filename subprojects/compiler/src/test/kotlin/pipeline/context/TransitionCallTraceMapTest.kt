/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.context

import hu.bme.mit.semantifyr.compiler.pipeline.artifact.TransitionCallTrace
import hu.bme.mit.semantifyr.oxsts.model.oxsts.impl.OxstsFactoryImpl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class TransitionCallTraceMapTest {
    private val factory = OxstsFactoryImpl()

    @Test
    fun `getTransitionCallTrace returns the trace stored under the trace operation name`() {
        val trace: TransitionCallTrace = mock()
        val map = TransitionCallTraceMap(mapOf("t1" to trace))
        val op = factory.createTraceOperation().also { it.name = "t1" }

        assertThat(map.getTransitionCallTrace(op)).isSameAs(trace)
    }

    @Test
    fun `getTransitionCallTrace throws when the operation name is unknown`() {
        val map = TransitionCallTraceMap(emptyMap())
        val op = factory.createTraceOperation().also { it.name = "missing" }

        assertThatThrownBy {
            map.getTransitionCallTrace(op)
        }.isInstanceOf(IllegalStateException::class.java).hasMessageContaining("missing")
    }

    @Test
    fun `empty map throws for any lookup`() {
        val map = TransitionCallTraceMap(emptyMap())
        val op = factory.createTraceOperation().also { it.name = "x" }

        assertThatThrownBy {
            map.getTransitionCallTrace(op)
        }.isInstanceOf(IllegalStateException::class.java)
    }
}
