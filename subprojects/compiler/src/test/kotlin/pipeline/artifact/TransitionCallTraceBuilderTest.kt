/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.artifact

import hu.bme.mit.semantifyr.compiler.pipeline.expression.CompileTimeExpressionEvaluator
import hu.bme.mit.semantifyr.compiler.pipeline.expression.CompileTimeExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TraceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.impl.OxstsFactoryImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TransitionCallTraceBuilderTest {
    private val evaluatorProvider: CompileTimeExpressionEvaluatorProvider = mock()
    private val evaluator: CompileTimeExpressionEvaluator = mock()
    private val instance: Instance = mock()
    private val container: Instance = mock()

    private val emptyCallExpression: CallSuffixExpression = OxstsFactoryImpl().createCallSuffixExpression()

    private fun mockTransitionDeclaration(): TransitionDeclaration {
        return mock()
    }

    private fun newBuilder(): TransitionCallTraceBuilder {
        whenever(evaluatorProvider.getEvaluator(any())).thenReturn(evaluator)
        return TransitionCallTraceBuilder(evaluatorProvider)
    }

    @Test
    fun `build on empty builder returns a map that fails lookup for any trace`() {
        val map = newBuilder().build()

        // Looking up anything in the empty map must raise.
        val traceOperation: TraceOperation = OxstsFactoryImpl().createTraceOperation().also { it.name = "missing" }

        assertThat(
            runCatching {
                map.getTransitionCallTrace(traceOperation)
            }.isFailure,
        ).isTrue
    }

    @Test
    fun `traceTransitionCall assigns unique tracer names for consecutive calls`() {
        val builder = newBuilder()

        val first = builder.traceTransitionCall(instance, container, mockTransitionDeclaration(), emptyCallExpression)
        val second = builder.traceTransitionCall(instance, container, mockTransitionDeclaration(), emptyCallExpression)
        val third = builder.traceTransitionCall(instance, container, mockTransitionDeclaration(), emptyCallExpression)

        val names = listOf(first, second, third).map { (it as TraceOperation).name }

        assertThat(names).hasSize(3)
        assertThat(names).doesNotHaveDuplicates()
        names.forEach {
            assertThat(it).startsWith("__transition_tracer")
        }
    }

    @Test
    fun `traceTransitionCall returns a TraceOperation`() {
        val builder = newBuilder()

        val operation = builder.traceTransitionCall(
            instance,
            container,
            mockTransitionDeclaration(),
            emptyCallExpression,
        )

        assertThat(operation).isInstanceOf(TraceOperation::class.java)
    }

    @Test
    fun `build returns a map that resolves every traced operation to its recorded trace`() {
        val builder = newBuilder()
        val transition = mockTransitionDeclaration()

        val traced = builder.traceTransitionCall(instance, container, transition, emptyCallExpression) as TraceOperation
        val map = builder.build()

        val recorded = map.getTransitionCallTrace(traced)
        assertThat(recorded.transitionDeclaration).isSameAs(transition)
        assertThat(recorded.self.instances).containsExactly(container)
    }

    @Test
    fun `build is a snapshot, later traces do not appear in an earlier build result`() {
        val builder = newBuilder()
        val snapshot = builder.build()

        val traced = builder.traceTransitionCall(
            instance,
            container,
            mockTransitionDeclaration(),
            emptyCallExpression,
        ) as TraceOperation

        assertThat(
            runCatching {
                snapshot.getTransitionCallTrace(traced)
            }.isFailure,
        ).isTrue
    }
}
