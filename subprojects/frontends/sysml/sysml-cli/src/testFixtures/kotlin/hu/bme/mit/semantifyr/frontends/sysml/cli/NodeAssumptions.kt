/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.cli

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.IOException

fun assumeNodeOnPath() {
    val available = try {
        ProcessBuilder("node", "--version").redirectErrorStream(true).start().waitFor() == 0
    } catch (_: IOException) {
        false
    }
    assumeTrue(available, "transforming SysML v2 needs node on PATH")
}
