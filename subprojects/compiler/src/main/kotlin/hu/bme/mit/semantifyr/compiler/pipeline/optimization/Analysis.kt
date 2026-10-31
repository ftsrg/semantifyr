/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization

import hu.bme.mit.semantifyr.compiler.pipeline.context.EvaluableCompilationContext
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.loggerFactory
import kotlin.time.measureTimedValue

interface Analysis<T : Any> {
    val key: Class<out Analysis<*>>
        get() = javaClass

    fun compute(input: EvaluableCompilationContext): T
}

class AnalysisManager(
    val analyses: List<Analysis<*>>,
) {
    private val logger by loggerFactory()

    private val analysisMap: Map<Class<out Analysis<*>>, Analysis<*>> = analyses.associateBy {
        it.key
    }

    private val cache = mutableMapOf<Class<*>, Any>()

    private val computations = LinkedHashMap<String, AnalysisStats>()

    val statistics: List<AnalysisStats>
        get() = computations.values.toList()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any, A : Analysis<T>> get(type: Class<A>, input: EvaluableCompilationContext): T {
        return cache.getOrPut(type) {
            val analysis = analysisMap[type] ?: error("Analysis ${type.simpleName} is not registered in this AnalysisManager")
            val (result, duration) = measureTimedValue {
                (analysis as Analysis<T>).compute(input)
            }
            logger.debug { "Analysis ${type.simpleName} computed in $duration" }
            val previous = computations[type.simpleName] ?: AnalysisStats(type.simpleName)
            computations[type.simpleName] = previous.copy(
                duration = previous.duration + duration,
                computations = previous.computations + 1,
            )
            result
        } as T
    }

    fun invalidateAll() {
        cache.clear()
    }

    fun resetStatistics() {
        computations.clear()
    }

}
