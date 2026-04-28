/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend

import java.util.Properties
import kotlin.time.Duration
import kotlin.time.TimeSource

object BuildInfo {
    val commit: String
    val buildTime: String

    private val startMark = TimeSource.Monotonic.markNow()

    init {
        val properties = Properties()
        val stream = BuildInfo::class.java.classLoader.getResourceAsStream("build-info.properties")
        if (stream != null) {
            properties.load(stream)
        }
        commit = properties.getProperty("commit", "unknown")
        buildTime = properties.getProperty("buildTime", "unknown")
    }

    val uptime: Duration
        get() = startMark.elapsedNow()
}
