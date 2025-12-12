/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.utils

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance

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

fun Instance.parentIterator() = InstanceParentIterator(this)

fun Instance.parentSequence() = parentIterator().asSequence()
