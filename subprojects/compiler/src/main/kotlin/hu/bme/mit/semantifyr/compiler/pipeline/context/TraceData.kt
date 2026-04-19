/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.context

import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TraceOperation
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.TransitionCallTrace

class TransitionCallTraceMap(
    private val traceMap: Map<String, TransitionCallTrace>,
) {

    fun getTransitionCallTrace(traceOperation: TraceOperation): TransitionCallTrace {
        return traceMap[traceOperation.name] ?: error("No transition call trace was found for '${traceOperation.name}'!")
    }

}

class InstanceIdMapping(
    private val instanceToId: Map<Instance, Int>,
    private val idToInstance: Map<Int, Instance>,
) {

    val entries: Set<Map.Entry<Instance, Int>>
        get() = instanceToId.entries

    fun instanceOfId(id: Int): Instance {
        return idToInstance[id] ?: error("Unknown instance id: $id")
    }

}
