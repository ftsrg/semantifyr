/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class Campaign(
    val name: String,
    val dimensions: List<Dimension<*>>,
    val filters: List<(Cell) -> Boolean>,
    val repetitions: Int,
    val warmups: Int,
    val timeout: Duration,
) {
    init {
        require(name.matches(CAMPAIGN_NAME)) { "Campaign name $name is not a lowercase identifier" }
        require(dimensions.isNotEmpty()) { "Campaign $name declares no axis" }
        require(repetitions >= 1) { "Campaign $name needs at least one repetition" }
        require(warmups >= 0) { "Campaign $name has a negative warmup count" }
        require(timeout.isPositive()) { "Campaign $name needs a positive timeout" }
    }

    fun cells(): List<Cell> {
        return permute(dimensions).filter { cell ->
            filters.all {
                it(cell)
            }
        }
    }

    companion object {
        private val CAMPAIGN_NAME = Regex("[a-z][a-z0-9-]*")
    }
}

class CampaignBuilder(
    private val name: String,
) {
    var repetitions = 3
    var warmups = 1
    var timeout = 5.minutes

    private val dimensions = mutableListOf<Dimension<*>>()
    private val filters = mutableListOf<(Cell) -> Boolean>()

    fun <T> axis(axis: Axis<T>, values: List<T>) {
        dimensions += Dimension(axis) {
            values
        }
    }

    fun <T> axis(axis: Axis<T>, values: (Cell) -> List<T>) {
        dimensions += Dimension(axis, values)
    }

    fun filter(predicate: (Cell) -> Boolean) {
        filters += predicate
    }

    fun build(): Campaign {
        return Campaign(name, dimensions.toList(), filters.toList(), repetitions, warmups, timeout)
    }
}

fun campaign(name: String, block: CampaignBuilder.() -> Unit): Campaign {
    return CampaignBuilder(name).apply(block).build()
}
