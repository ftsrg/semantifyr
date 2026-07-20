/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalEdge
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalLocation
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalLocationKind

class UppaalEmissionContext {

    val locations = mutableListOf<UppaalLocation>()
    val edges = mutableListOf<UppaalEdge>()
    private var nextLocationId = 0
    private var nextHavocId = 0

    fun freshCommitted(name: String = "c"): UppaalLocation {
        val id = "loc_$nextLocationId"
        nextLocationId++
        val location = UppaalLocation(id, "${name}_$id", UppaalLocationKind.Committed)
        locations += location
        return location
    }

    fun freshHavocSelectName(variableName: String): String {
        val name = "havoc_${variableName}_$nextHavocId"
        nextHavocId++
        return name
    }
}
