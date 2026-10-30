/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization

import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.loggerFactory
import org.eclipse.emf.ecore.EObject
import kotlin.time.TimeSource.Monotonic.markNow

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

    fun isNotEmpty(): Boolean {
        return queue.isNotEmpty()
    }
}

interface OptimizationPattern {
    fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean
}

abstract class CompositeOptimizationPattern : OptimizationPattern {

    protected abstract val patterns: Collection<OptimizationPattern>

    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        for (pattern in patterns) {
            if (pattern.tryApply(element, worklist)) {
                return true
            }
        }

        return false
    }

}

class PatternOptimizer(
    private val patterns: List<OptimizationPattern>,
    private val pass: CompilationPass,
    private val artifactManager: CompilationArtifactManager,
) : Optimizer<EObject> {

    private val logger by loggerFactory()

    override fun optimize(input: EObject): Boolean {
        if (patterns.isEmpty()) {
            return false
        }

        val totalMark = markNow()

        val worklist = Worklist<EObject>()
        worklist.add(input)
        input.eAllOfType<EObject>().forEach {
            worklist.add(it)
        }
        val seedElapsed = totalMark.elapsedNow()

        var changed = false
        var pops = 0
        var applications = 0
        while (worklist.isNotEmpty()) {
            val current = worklist.pop()
            pops++
            if (current !== input && current.eResource() == null) {
                continue
            }
            for (pattern in patterns) {
                if (pattern.tryApply(current, worklist)) {
                    changed = true
                    applications++
                    artifactManager.commitStep(pass)
                    break
                }
            }
        }

        val totalElapsed = totalMark.elapsedNow()
        logger.debug {
            "WorklistOptimizer[${pass.name}]: $totalElapsed total (seed $seedElapsed), $pops pop(s), $applications application(s), ${patterns.size} pattern(s), changed=$changed"
        }
        return changed
    }

}
