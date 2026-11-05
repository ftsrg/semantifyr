/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.cli

import org.junit.jupiter.api.Assumptions.assumeTrue

// the bundled SysIDE CLI that translates a model runs on Node.js
fun assumeNodeOnPath() {
    val available = runCatching {
        ProcessBuilder("node", "--version").redirectErrorStream(true).start().waitFor() == 0
    }.getOrDefault(false)
    assumeTrue(available, "translating SysML v2 needs node on PATH")
}
