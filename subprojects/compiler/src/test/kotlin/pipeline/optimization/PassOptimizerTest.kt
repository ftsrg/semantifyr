/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization

import hu.bme.mit.semantifyr.compiler.pipeline.context.EvaluableCompilationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class PassOptimizerTest {
    private class StubAnalysis : Analysis<Any> {
        override fun compute(input: EvaluableCompilationContext): Any {
            return Unit
        }
    }

    private class Bag(
        var value: Int = 0,
    )

    @Test
    fun `empty pass list reports no change`() {
        val manager = AnalysisManager(emptyList())
        val optimizer = PassOptimizer<Bag>(emptyList(), manager)

        assertThat(optimizer.optimize(Bag())).isFalse
    }

    @Test
    fun `passes that return Unchanged cause one iteration and report no change`() {
        val manager = mock<AnalysisManager>()
        val pass1: Pass<Bag> = mock {
            on {
                run(any(), any())
            } doReturn PassResult.Unchanged
        }
        val pass2: Pass<Bag> = mock {
            on {
                run(any(), any())
            } doReturn PassResult.Unchanged
        }

        val optimizer = PassOptimizer(listOf(pass1, pass2), manager)
        val changed = optimizer.optimize(Bag())

        assertThat(changed).isFalse
        verify(pass1, times(1)).run(any(), eq(manager))
        verify(pass2, times(1)).run(any(), eq(manager))
        verify(manager, times(1)).invalidateAll()
    }

    @Test
    fun `a changing pass triggers another iteration`() {
        val manager = mock<AnalysisManager>()
        var firstCall = true
        val pass: Pass<Bag> = mock {
            on {
                run(any(), any())
            } doAnswer {
                if (firstCall) {
                    firstCall = false
                    PassResult.Changed
                } else {
                    PassResult.Unchanged
                }
            }
        }

        val optimizer = PassOptimizer(listOf(pass), manager)
        val changed = optimizer.optimize(Bag())

        assertThat(changed).isTrue
        verify(pass, times(2)).run(any(), eq(manager))
    }

    @Test
    fun `pipeline converges even when a pass changes the IR multiple times`() {
        val manager = mock<AnalysisManager>()
        var runs = 0
        val pass1: Pass<Bag> = mock {
            on {
                run(any(), any())
            } doAnswer {
                runs++
                if (runs <= 2) {
                    PassResult.Changed
                } else {
                    PassResult.Unchanged
                }
            }
        }
        val pass2: Pass<Bag> = mock {
            on {
                run(any(), any())
            } doReturn PassResult.Unchanged
        }

        val changed = PassOptimizer(listOf(pass1, pass2), manager).optimize(Bag())

        assertThat(changed).isTrue
        verify(pass1, times(3)).run(any(), eq(manager))
        verify(pass2, times(3)).run(any(), eq(manager))
    }
}
