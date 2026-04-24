/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization

import hu.bme.mit.semantifyr.compiler.pipeline.context.EvaluableCompilationContext
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.loggerFactory
import kotlin.time.TimeSource.Monotonic.markNow

interface Analysis<T : Any> {
    val key: Class<out Analysis<*>>
        get() = javaClass

    fun compute(input: EvaluableCompilationContext): T
}

class AnalysisManager(
    val analyses: List<Analysis<*>>
) {
    private val logger by loggerFactory()

    private val analysisMap: Map<Class<out Analysis<*>>, Analysis<*>> = analyses.associateBy {
        it.key
    }

    private val cache = mutableMapOf<Class<*>, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any, A : Analysis<T>> get(type: Class<A>, input: EvaluableCompilationContext): T {
        return cache.getOrPut(type) {
            val analysis = analysisMap[type] ?: error("Analysis ${type.simpleName} is not registered in this AnalysisManager")
            val mark = markNow()
            val result = (analysis as Analysis<T>).compute(input)
            logger.debug { "Analysis ${type.simpleName} computed in ${mark.elapsedNow()}" }
            result
        } as T
    }

    fun invalidateAll() {
        cache.clear()
    }

    fun invalidateExcept(preserved: Set<Class<out Analysis<*>>>) {
        cache.keys.retainAll(preserved)
    }

}
