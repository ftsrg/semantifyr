/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend

import com.google.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

@Singleton
class ServerStatus {

    private lateinit var startedAtInstant: Instant

    fun markStartNow() {
        startedAtInstant = Clock.System.now()
    }

    val startedAt: Instant
        get() = startedAtInstant

    val uptime: Duration
        get() = Clock.System.now() - startedAtInstant
}
