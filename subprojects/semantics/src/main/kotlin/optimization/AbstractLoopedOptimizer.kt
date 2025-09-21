/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.optimization

abstract class AbstractLoopedOptimizer<T> {
    fun optimize(element: T): Boolean {
        var didAnyWork = false
        var workRemaining = true

        while (workRemaining) {
            workRemaining = doOptimizationStep(element)

            if (workRemaining) {
                didAnyWork = true
            }
        }

        ensureWellFormedness(element)

        return didAnyWork
    }

    protected abstract fun doOptimizationStep(element: T): Boolean
    protected open fun ensureWellFormedness(element: T) {

    }
}
