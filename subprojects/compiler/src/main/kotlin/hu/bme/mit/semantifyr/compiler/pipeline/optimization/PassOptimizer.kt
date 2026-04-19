/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization

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
) {

    fun run(input: T): Boolean {
        analysisManager.invalidateAll()

        var changed = false
        var iteration = true
        while (iteration) {
            iteration = false
            for (pass in passes) {
                val result = pass.run(input, analysisManager)
                if (result.changed) {
                    changed = true
                    iteration = true
                    analysisManager.invalidateExcept(result.preserved)
                }
            }
        }
        return changed
    }

}
