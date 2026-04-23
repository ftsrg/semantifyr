/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization

import hu.bme.mit.semantifyr.compiler.pipeline.context.EvaluableCompilationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PassResultTest {

    private class FakeAnalysisA : Analysis<Any> {
        override fun compute(input: EvaluableCompilationContext): Any {
            return Unit
        }
    }

    private class FakeAnalysisB : Analysis<Any> {
        override fun compute(input: EvaluableCompilationContext): Any {
            return Unit
        }
    }

    @Test
    fun `Changed_preserving records the preserved analyses`() {
        val result = PassResult.Changed.preserving(FakeAnalysisA::class.java, FakeAnalysisB::class.java)
        assertThat(result.preserved).containsExactlyInAnyOrder(FakeAnalysisA::class.java, FakeAnalysisB::class.java)
    }
}
