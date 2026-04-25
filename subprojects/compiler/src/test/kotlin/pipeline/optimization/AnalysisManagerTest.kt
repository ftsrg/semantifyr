/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization

import hu.bme.mit.semantifyr.compiler.pipeline.context.EvaluableCompilationContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class AnalysisManagerTest {
    private class ResultA(
        val value: String,
    )

    private class ResultB(
        val value: String,
    )

    private class AnalysisA(
        private val result: ResultA,
    ) : Analysis<ResultA> {
        var computeCount = 0
            private set

        override fun compute(input: EvaluableCompilationContext): ResultA {
            computeCount++
            return result
        }
    }

    private class AnalysisB(
        private val result: ResultB,
    ) : Analysis<ResultB> {
        var computeCount = 0
            private set

        override fun compute(input: EvaluableCompilationContext): ResultB {
            computeCount++
            return result
        }
    }

    private val context: EvaluableCompilationContext = mock()

    @Test
    fun `get computes and returns the analysis result`() {
        val analysis = AnalysisA(ResultA("hello"))
        val manager = AnalysisManager(listOf(analysis))

        val result = manager.get(AnalysisA::class.java, context)

        assertThat(result.value).isEqualTo("hello")
    }

    @Test
    fun `repeated get returns the cached result without recomputing`() {
        val analysis = AnalysisA(ResultA("r"))
        val manager = AnalysisManager(listOf(analysis))

        manager.get(AnalysisA::class.java, context)
        manager.get(AnalysisA::class.java, context)
        manager.get(AnalysisA::class.java, context)

        assertThat(analysis.computeCount).isEqualTo(1)
    }

    @Test
    fun `invalidateAll clears the cache so the next get recomputes`() {
        val analysis = AnalysisA(ResultA("r"))
        val manager = AnalysisManager(listOf(analysis))

        manager.get(AnalysisA::class.java, context)
        manager.invalidateAll()
        manager.get(AnalysisA::class.java, context)

        assertThat(analysis.computeCount).isEqualTo(2)
    }

    @Test
    fun `invalidateExcept keeps only the preserved entries`() {
        val a = AnalysisA(ResultA("a"))
        val b = AnalysisB(ResultB("b"))
        val manager = AnalysisManager(listOf(a, b))

        manager.get(AnalysisA::class.java, context)
        manager.get(AnalysisB::class.java, context)
        manager.invalidateExcept(setOf(AnalysisA::class.java))

        manager.get(AnalysisA::class.java, context)
        manager.get(AnalysisB::class.java, context)

        assertThat(a.computeCount).isEqualTo(1)
        assertThat(b.computeCount).isEqualTo(2)
    }

    @Test
    fun `invalidateExcept with empty set clears everything`() {
        val a = AnalysisA(ResultA("a"))
        val manager = AnalysisManager(listOf(a))

        manager.get(AnalysisA::class.java, context)
        manager.invalidateExcept(emptySet())
        manager.get(AnalysisA::class.java, context)

        assertThat(a.computeCount).isEqualTo(2)
    }

    @Test
    fun `get for unregistered analysis type throws a descriptive error`() {
        val manager = AnalysisManager(emptyList())

        assertThatThrownBy {
            manager.get(AnalysisA::class.java, context)
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("AnalysisA")
            .hasMessageContaining("not registered")
    }
}
