/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalEdge
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalLocation
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalLocationKind
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

// a guard's atoms are tested before its resets take effect
class UppaalClockEdge(
    val clockAtoms: List<UppaalClockAtom>,
    val resets: List<VariableDeclaration>,
)

class UppaalEmissionContext {

    val locations = mutableListOf<UppaalLocation>()
    val edges = mutableListOf<UppaalEdge>()

    // keyed by the name of the location the edge enters, which no other edge enters
    val clockEdges = mutableMapOf<String, UppaalClockEdge>()
    private var nextLocationId = 0
    private var nextHavocId = 0

    fun freshCommitted(name: String = "c"): UppaalLocation {
        val id = "loc_$nextLocationId"
        nextLocationId++
        val location = UppaalLocation(id, "${name}_$id", UppaalLocationKind.Committed)
        locations += location
        return location
    }

    fun freshClockLocation(clockEdge: UppaalClockEdge): UppaalLocation {
        val location = freshCommitted("clock")
        clockEdges[location.name] = clockEdge
        return location
    }

    fun freshHavocSelectName(variableName: String): String {
        val name = "havoc_${variableName}_$nextHavocId"
        nextHavocId++
        return name
    }
}
