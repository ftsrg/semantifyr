/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.optimizers

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Analysis
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.AnalysisManager
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Optimizer
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.TimeSource.Monotonic.markNow

interface Pass<T> {
    fun run(input: T, analyses: AnalysisManager): PassResult
}

data class PassResult(
    val changed: Boolean,
    val preserved: Set<Class<out Analysis<*>>>,
) {
    companion object {
        private val PreservedAll: Set<Class<out Analysis<*>>> = emptySet()

        val Unchanged = PassResult(changed = false, preserved = PreservedAll)

        fun changed(): PassResult {
            return PassResult(changed = true, preserved = emptySet())
        }

        fun changed(vararg preserved: Class<out Analysis<*>>): PassResult {
            return PassResult(changed = true, preserved = preserved.toSet())
        }
    }
}

class PassOptimizer<T>(
    private val passes: List<Pass<T>>,
    private val analysisManager: AnalysisManager,
) : Optimizer<T>() {

    private val logger by loggerFactory()

    override fun optimize(input: T): Boolean {
        analysisManager.invalidateAll()

        val totalMark = markNow()

        // Per-pass aggregate timing and firing counts, for the end-of-run report.
        val passTotal = HashMap<String, Duration>()
        val passFires = HashMap<String, Int>()
        val passRuns = HashMap<String, Int>()

        var changed = false
        var iteration = true
        var round = 0
        while (iteration) {
            round++
            val roundMark = markNow()
            logger.debug { "Optimizer fixpoint round $round" }
            iteration = false
            var roundFires = 0
            for (pass in passes) {
                val passName = pass::class.simpleName ?: "<pass>"
                val passMark = markNow()
                val result = pass.run(input, analysisManager)
                val passElapsed = passMark.elapsedNow()
                passTotal[passName] = (passTotal[passName] ?: ZERO) + passElapsed
                passRuns[passName] = (passRuns[passName] ?: 0) + 1
                if (result.changed) {
                    passFires[passName] = (passFires[passName] ?: 0) + 1
                    roundFires++
                    logger.debug { "  $passName changed the model in ${passElapsed}" }
                    changed = true
                    iteration = true
                    analysisManager.invalidateExcept(result.preserved)
                } else {
                    logger.debug { "  $passName unchanged (${passElapsed})" }
                }
            }
            logger.debug { "Round $round done in ${roundMark.elapsedNow()} with $roundFires pass(es) changed" }
        }

        val total = totalMark.elapsedNow()
        logger.info { "Optimizer fixpoint: ${round} round(s), ${total} total, changed=$changed" }
        for ((name, dur) in passTotal.entries.sortedByDescending { it.value }) {
            logger.info {
                "  $name: ${dur} total, ${passRuns[name]} run(s), ${passFires[name] ?: 0} firing(s)"
            }
        }
        return changed
    }

}
