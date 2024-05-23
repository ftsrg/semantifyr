/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.engine.utils

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import java.util.*

class InstanceTreeIterator(
    instance: Instance
) : Iterator<Instance> {

    private val instanceQueue = LinkedList<Instance>()

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

fun Instance.treeSequence() = Sequence {
    treeIterator()
}

fun Instance.treeIterator() = InstanceTreeIterator(this)
