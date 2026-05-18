/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.context

import hu.bme.mit.semantifyr.compiler.pipeline.inlining.TransitionCallTrace
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class TransitionCallTraceMap(
    private val traceMap: Map<VariableDeclaration, TransitionCallTrace>,
) {

    fun getTransitionCallTrace(variable: VariableDeclaration): TransitionCallTrace {
        return traceMap[variable] ?: error("No transition call trace was found for '${variable.name}'")
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
