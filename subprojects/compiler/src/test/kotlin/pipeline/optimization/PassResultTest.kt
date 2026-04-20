/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization

import hu.bme.mit.semantifyr.compiler.pipeline.context.EvaluableCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.optimizers.PassResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PassResultTest {

    private class FakeAnalysisA : Analysis<Any> {
        override fun compute(input: EvaluableCompilationContext): Any = Unit
    }

    private class FakeAnalysisB : Analysis<Any> {
        override fun compute(input: EvaluableCompilationContext): Any = Unit
    }

    @Test
    fun `Unchanged reports no change and an empty preserved set`() {
        val result = PassResult.Unchanged
        assertThat(result.changed).isFalse
        assertThat(result.preserved).isEmpty()
    }

    @Test
    fun `changed() with no arguments reports change and preserves nothing`() {
        val result = PassResult.changed()
        assertThat(result.changed).isTrue
        assertThat(result.preserved).isEmpty()
    }

    @Test
    fun `changed(vararg) records the preserved analyses`() {
        val result = PassResult.changed(FakeAnalysisA::class.java, FakeAnalysisB::class.java)
        assertThat(result.changed).isTrue
        assertThat(result.preserved).containsExactlyInAnyOrder(FakeAnalysisA::class.java, FakeAnalysisB::class.java)
    }

    @Test
    fun `changed(vararg) deduplicates repeated analysis types`() {
        val result = PassResult.changed(FakeAnalysisA::class.java, FakeAnalysisA::class.java)
        assertThat(result.preserved).containsExactly(FakeAnalysisA::class.java)
    }

    @Test
    fun `equal results compare equal by value`() {
        val a = PassResult.changed(FakeAnalysisA::class.java)
        val b = PassResult.changed(FakeAnalysisA::class.java)
        assertThat(a).isEqualTo(b)
    }
}
