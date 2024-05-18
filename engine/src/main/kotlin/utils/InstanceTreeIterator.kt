package hu.bme.mit.gamma.oxsts.engine.utils

import hu.bme.mit.gamma.oxsts.model.oxsts.Instance
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
