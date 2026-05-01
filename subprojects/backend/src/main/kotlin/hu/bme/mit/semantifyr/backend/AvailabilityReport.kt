/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

sealed interface AvailabilityReport {
    object Available : AvailabilityReport {
        override val isUsable = true
    }

    data class Unavailable(
        val reason: String,
        val hints: List<String> = emptyList(),
    ) : AvailabilityReport {
        override val isUsable = false
    }

    val isUsable: Boolean

}
