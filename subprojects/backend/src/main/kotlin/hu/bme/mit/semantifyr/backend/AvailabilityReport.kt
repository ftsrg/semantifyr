/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

sealed interface AvailabilityReport {
    object Available : AvailabilityReport

    data class Degraded(val message: String) : AvailabilityReport

    data class Unavailable(
        val reason: String,
        val hints: List<String> = emptyList(),
    ) : AvailabilityReport

    val isUsable: Boolean
        get() = this is Available || this is Degraded
}
