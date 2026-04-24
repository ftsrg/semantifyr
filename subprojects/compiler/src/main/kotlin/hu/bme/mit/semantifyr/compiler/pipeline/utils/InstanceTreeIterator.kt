/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.utils

import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance

class InstanceTreeIterator(
    instance: Instance
) : Iterator<Instance> {

    private val instanceQueue = ArrayDeque<Instance>()

    init {
        instanceQueue += instance
    }

    override fun hasNext(): Boolean {
        return instanceQueue.any()
    }

    override fun next(): Instance {
        val instance = instanceQueue.removeFirst()
        instanceQueue += instance.children
        return instance
    }

}

fun Instance.treeIterator(): InstanceTreeIterator {
    return InstanceTreeIterator(this)
}

fun Instance.treeSequence(): Sequence<Instance> {
    return treeIterator().asSequence()
}
