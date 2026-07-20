/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization

import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import kotlin.time.Duration
import kotlin.time.TimeSource.Monotonic.markNow

interface Pass<T> {
    fun run(input: T, analysisManager: AnalysisManager): PassResult
}

sealed interface PassResult {

    object Unchanged : PassResult

    object Changed : PassResult

}

private data class PassStats(
    var total: Duration = Duration.ZERO,
    var runs: Int = 0,
    var fires: Int = 0,
)

class PassOptimizer<T>(
    private val passes: List<Pass<T>>,
    private val analysisManager: AnalysisManager,
) : Optimizer<T> {

    private val logger by loggerFactory()

    override fun optimize(input: T): Boolean {
        analysisManager.invalidateAll()

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
                val passName = pass::class.simpleName ?: "<pass>"
                val passMark = markNow()
                val result = pass.run(input, analysisManager)
                val passElapsed = passMark.elapsedNow()
                val passStats = stats.getOrPut(passName) { PassStats() }
                passStats.total += passElapsed
                passStats.runs++
                when (result) {
                    is PassResult.Changed -> {
                        passStats.fires++
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
            }
            logger.debug { "Round $round done in ${roundMark.elapsedNow()} with $roundFires pass(es) changed" }
        }

        val total = totalMark.elapsedNow()
        logger.info { "Optimizer fixpoint: $round round(s), $total total, changed=$changed" }
        for ((name, passStats) in stats.entries.sortedByDescending { it.value.total }) {
            logger.info {
                "  $name: ${passStats.total} total, ${passStats.runs} run(s), ${passStats.fires} firing(s)"
            }
        }
        return changed
    }

}
