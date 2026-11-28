/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend

import java.util.Properties

object BuildInfo {
    val version = javaClass.`package`.implementationVersion ?: "unknown"
    val commit: String

    init {
        val properties = Properties()
        val stream = BuildInfo::class.java.classLoader.getResourceAsStream("build-info.properties")
        if (stream != null) {
            properties.load(stream)
        }
        commit = properties.getProperty("commit", "unknown")
    }
}
