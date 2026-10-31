/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization

import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import kotlin.time.TimeSource.Monotonic.markNow

interface Pass<T> {
    fun run(input: T, analysisManager: AnalysisManager): PassResult
}

sealed interface PassResult {

    object Unchanged : PassResult

    object Changed : PassResult

}

class PassOptimizer<T>(
    private val passes: List<Pass<T>>,
    private val analysisManager: AnalysisManager,
) : Optimizer<T> {

    private val logger by loggerFactory()

    override fun optimize(input: T): OptimizationStats {
        analysisManager.invalidateAll()
        analysisManager.resetStatistics()

        val totalMark = markNow()
        val stats = LinkedHashMap<String, PassStats>()

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
                val passName = pass.javaClass.simpleName
                val passMark = markNow()
                val result = pass.run(input, analysisManager)
                val passElapsed = passMark.elapsedNow()
                val previous = stats[passName] ?: PassStats(passName)
                var firings = previous.firings
                when (result) {
                    is PassResult.Changed -> {
                        firings++
                        roundFires++
                        logger.debug { "  $passName changed the model in $passElapsed" }
                        changed = true
                        iteration = true
                        analysisManager.invalidateAll()
                    }
                    PassResult.Unchanged -> {
                        logger.debug { "  $passName unchanged ($passElapsed)" }
                    }
                }
                stats[passName] = previous.copy(
                    duration = previous.duration + passElapsed,
                    runs = previous.runs + 1,
                    firings = firings,
                )
            }
            logger.debug { "Round $round done in ${roundMark.elapsedNow()} with $roundFires pass(es) changed" }
        }

        val total = totalMark.elapsedNow()
        logger.info { "Optimizer fixpoint: $round round(s), $total total, changed=$changed" }
        for (passStats in stats.values.sortedByDescending { it.duration }) {
            logger.info {
                "  ${passStats.name}: ${passStats.duration} total, ${passStats.runs} run(s), ${passStats.firings} firing(s)"
            }
        }
        return OptimizationStats(
            duration = total,
            passes = stats.values.toList(),
            analyses = analysisManager.statistics,
        )
    }

}
