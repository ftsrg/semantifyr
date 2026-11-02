/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios

import hu.bme.mit.semantifyr.backends.nuxmv.transformation.NuxmvCapabilityChecker
import hu.bme.mit.semantifyr.backends.spin.transformation.SpinCapabilityChecker
import hu.bme.mit.semantifyr.backends.theta.transformation.xsts.ThetaCapabilityChecker
import hu.bme.mit.semantifyr.backends.uppaal.transformation.UppaalCapabilityChecker
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

// writes the website's limitations table: backend, configuration, construct and reason per line
fun main(args: Array<String>) {
    val limitations = mapOf(
        "theta" to ThetaCapabilityChecker.limitations,
        "nuxmv" to NuxmvCapabilityChecker.limitations,
        "spin" to SpinCapabilityChecker.limitations,
        "uppaal" to UppaalCapabilityChecker.limitations,
    )
    val rows = limitations.flatMap { (backend, entries) ->
        entries.map {
            listOf(backend, it.configuration.orEmpty(), it.construct, it.reason).joinToString("\t")
        }
    }
    val table = (listOf("backend\tconfiguration\tconstruct\treason") + rows).joinToString("\n", postfix = "\n")
    Path(args.single()).createParentDirectories().writeText(table)
}
