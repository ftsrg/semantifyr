/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.utils

import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance

class InstanceParentIterator(
    instance: Instance
) : Iterator<Instance> {

    private var currentInstance: Instance? = instance

    override fun hasNext(): Boolean {
        return currentInstance != null
    }

    override fun next(): Instance {
        val instance = currentInstance ?: error("No next element in iterator!")
        currentInstance = instance.parent
        return instance
    }

}

fun Instance.parentIterator(): InstanceParentIterator {
    return InstanceParentIterator(this)
}

fun Instance.parentSequence(): Sequence<Instance> {
    return parentIterator().asSequence()
}
