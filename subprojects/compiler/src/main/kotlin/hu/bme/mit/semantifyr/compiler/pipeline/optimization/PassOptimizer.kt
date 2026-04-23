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

    data class Changed(
        val preserved: Set<Class<out Analysis<*>>> = emptySet(),
    ) : PassResult {
        companion object {
            fun preserving(vararg analyses: Class<out Analysis<*>>): Changed {
                return Changed(analyses.toSet())
            }
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

        val passTotal = LinkedHashMap<String, Duration>()
        val passFires = LinkedHashMap<String, Int>()
        val passRuns = LinkedHashMap<String, Int>()

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
                passTotal[passName] = (passTotal[passName] ?: Duration.ZERO) + passElapsed
                passRuns[passName] = (passRuns[passName] ?: 0) + 1
                when (result) {
                    is PassResult.Changed -> {
                        passFires[passName] = (passFires[passName] ?: 0) + 1
                        roundFires++
                        logger.debug { "  $passName changed the model in ${passElapsed}" }
                        changed = true
                        iteration = true
                        analysisManager.invalidateExcept(result.preserved)
                    }
                    PassResult.Unchanged -> {
                        logger.debug { "  $passName unchanged (${passElapsed})" }
                    }
                }
            }
            logger.debug { "Round $round done in ${roundMark.elapsedNow()} with $roundFires pass(es) changed" }
        }

        val total = totalMark.elapsedNow()
        logger.info { "Optimizer fixpoint: ${round} round(s), ${total} total, changed=$changed" }
        for ((name, dur) in passTotal.entries.sortedByDescending { it.value }) {
            logger.info {
                "  $name: $dur total, ${passRuns[name]} run(s), ${passFires[name] ?: 0} firing(s)"
            }
        }
        return changed
    }

}
