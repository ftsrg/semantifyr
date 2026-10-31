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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import kotlin.time.Duration.Companion.milliseconds

class PassOptimizerTest {
    private class StubAnalysis : Analysis<Any> {
        override fun compute(input: EvaluableCompilationContext): Any {
            return Unit
        }
    }

    private class Bag(
        var value: Int = 0,
    )

    private class IncrementsTwice : Pass<Bag> {
        override fun run(input: Bag, analysisManager: AnalysisManager): PassResult {
            if (input.value >= 2) {
                return PassResult.Unchanged
            }
            input.value++
            return PassResult.Changed
        }
    }

    private class ReadsAnalysis(
        private val context: EvaluableCompilationContext,
    ) : Pass<Bag> {
        override fun run(input: Bag, analysisManager: AnalysisManager): PassResult {
            analysisManager.get(StubAnalysis::class.java, context)
            return PassResult.Unchanged
        }
    }

    @Test
    fun `stats count runs and firings per pass and analysis recomputations`() {
        val manager = AnalysisManager(listOf(StubAnalysis()))
        val optimizer = PassOptimizer(listOf(IncrementsTwice(), ReadsAnalysis(mock())), manager)

        val stats = optimizer.optimize(Bag())

        assertThat(stats.changed).isTrue
        assertThat(stats.passes.map { Triple(it.name, it.runs, it.firings) }).containsExactly(
            Triple("IncrementsTwice", 3, 2),
            Triple("ReadsAnalysis", 3, 0),
        )
        assertThat(stats.analyses.map { it.name to it.computations }).containsExactly("StubAnalysis" to 2)
    }

    @Test
    fun `statistics restart with every optimize call`() {
        val manager = AnalysisManager(listOf(StubAnalysis()))
        val optimizer = PassOptimizer(listOf(IncrementsTwice(), ReadsAnalysis(mock())), manager)
        val bag = Bag()
        optimizer.optimize(bag)

        val stats = optimizer.optimize(bag)

        assertThat(stats.changed).isFalse
        assertThat(stats.passes.map { Triple(it.name, it.runs, it.firings) }).containsExactly(
            Triple("IncrementsTwice", 1, 0),
            Triple("ReadsAnalysis", 1, 0),
        )
        assertThat(stats.analyses.map { it.name to it.computations }).containsExactly("StubAnalysis" to 1)
    }

    @Test
    fun `merged stats sum per pass and keep first-seen order`() {
        val first = OptimizationStats(
            duration = 3.milliseconds,
            passes = listOf(PassStats("A", 1.milliseconds, 1, 1), PassStats("B", 2.milliseconds, 1, 0)),
            analyses = listOf(AnalysisStats("X", 1.milliseconds, 1)),
        )
        val second = OptimizationStats(
            duration = 4.milliseconds,
            passes = listOf(PassStats("B", 4.milliseconds, 2, 2)),
            analyses = listOf(AnalysisStats("X", 2.milliseconds, 3)),
        )

        val merged = first + second

        assertThat(merged.duration).isEqualTo(7.milliseconds)
        assertThat(merged.passes).containsExactly(
            PassStats("A", 1.milliseconds, 1, 1),
            PassStats("B", 6.milliseconds, 3, 2),
        )
        assertThat(merged.analyses).containsExactly(AnalysisStats("X", 3.milliseconds, 4))
    }

    @Test
    fun `empty pass list reports no change`() {
        val manager = AnalysisManager(emptyList())
        val optimizer = PassOptimizer<Bag>(emptyList(), manager)

        assertThat(optimizer.optimize(Bag()).changed).isFalse
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
        val changed = optimizer.optimize(Bag()).changed

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
        val changed = optimizer.optimize(Bag()).changed

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

        val changed = PassOptimizer(listOf(pass1, pass2), manager).optimize(Bag()).changed

        assertThat(changed).isTrue
        verify(pass1, times(3)).run(any(), eq(manager))
        verify(pass2, times(3)).run(any(), eq(manager))
    }
}
