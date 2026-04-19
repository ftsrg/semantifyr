/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization

import hu.bme.mit.semantifyr.compiler.pipeline.context.InstantiatedCompilationContext

interface Analysis<T : Any> {
    fun compute(input: InstantiatedCompilationContext): T
}

class AnalysisManager(
    private val analyses: Map<Class<out Analysis<*>>, Analysis<*>>,
) {

    private val cache = mutableMapOf<Class<*>, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any, A : Analysis<T>> get(type: Class<A>, input: InstantiatedCompilationContext): T {
        return cache.getOrPut(type) {
            val analysis = analyses[type] ?: error("Analysis ${type.simpleName} is not registered in this AnalysisManager")
            (analysis as Analysis<T>).compute(input)
        } as T
    }

    fun invalidateAll() {
        cache.clear()
    }

    fun invalidateExcept(preserved: Set<Class<out Analysis<*>>>) {
        cache.keys.retainAll(preserved)
    }

}
