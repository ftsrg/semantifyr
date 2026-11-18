/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import kotlinx.serialization.Serializable

data class ScheduledRepetition(
    val round: Int,
    val configuration: String,
)

@Serializable
data class InterleavedRounds(
    val repetitions: Int = 5,
) {
    init {
        require(repetitions >= 5) {
            "a benchmark repeats at least five times"
        }
    }

    fun order(configurations: List<String>): List<ScheduledRepetition> {
        return (0..<repetitions).flatMap { round ->
            configurations.map {
                ScheduledRepetition(round, it)
            }
        }
    }
}
