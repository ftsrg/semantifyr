/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization

import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import org.eclipse.emf.ecore.EObject

class Worklist<T> {
    private val queue = ArrayDeque<T>()
    private val inQueue = mutableSetOf<T>()

    fun add(item: T) {
        if (inQueue.add(item)) {
            queue.addLast(item)
        }
    }

    fun pop(): T {
        val item = queue.removeFirst()
        inQueue.remove(item)
        return item
    }

    fun isNotEmpty(): Boolean = queue.isNotEmpty()
}

interface OptimizationPattern {
    fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean
}

class WorklistOptimizer(
    private val patterns: List<OptimizationPattern>,
    private val pass: CompilationPass,
    private val artifactManager: CompilationArtifactManager,
) : Optimizer<EObject>() {

    override fun optimize(input: EObject): Boolean {
        if (patterns.isEmpty()) {
            return false
        }

        val worklist = Worklist<EObject>()
        worklist.add(input)
        input.eAllOfType<EObject>().forEach {
            worklist.add(it)
        }

        var changed = false
        while (worklist.isNotEmpty()) {
            val current = worklist.pop()
            for (pattern in patterns) {
                if (pattern.tryApply(current, worklist)) {
                    changed = true
                    artifactManager.commitStep(pass)
                    break
                }
            }
        }
        return changed
    }

}
