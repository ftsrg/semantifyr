/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class CampaignTest {

    private val model = Axis<String>("model") { it }
    private val case = Axis<Int>("case") { "case$it" }
    private val engine = Axis<String>("engine") { it }

    @Test
    fun `the framework permutes the axes in declaration order`() {
        val campaign = campaign("product") {
            axis(model, listOf("a", "b"))
            axis(engine, listOf("theta", "spin"))
        }

        assertThat(campaign.cells().map { it.key }).containsExactly(
            "model=a/engine=theta",
            "model=a/engine=spin",
            "model=b/engine=theta",
            "model=b/engine=spin",
        )
    }

    @Test
    fun `a dependent axis takes its values from the cell built so far`() {
        val casesOfModel = mapOf("a" to listOf(1, 2), "b" to listOf(3))
        val campaign = campaign("dependent") {
            axis(model, listOf("a", "b"))
            axis(case) {
                casesOfModel.getValue(it[model])
            }
        }

        val cells = campaign.cells()

        assertThat(cells.map { it.key }).containsExactly("model=a/case=case1", "model=a/case=case2", "model=b/case=case3")
        assertThat(cells.map { it[case] }).containsExactly(1, 2, 3)
        assertThat(cells.first().coordinates).containsExactly(entry("model", "a"), entry("case", "case1"))
    }

    @Test
    fun `the campaign filters the permuted cells`() {
        val campaign = campaign("filtered") {
            axis(model, listOf("small", "spacecraft"))
            axis(engine, listOf("theta", "nuxmv", "uppaal"))
            filter {
                it[model] != "spacecraft" || it[engine] == "nuxmv"
            }
        }

        assertThat(campaign.cells().map { it.key }).containsExactly(
            "model=small/engine=theta",
            "model=small/engine=nuxmv",
            "model=small/engine=uppaal",
            "model=spacecraft/engine=nuxmv",
        )
    }

    @Test
    fun `defaults are three repetitions after one warmup`() {
        val campaign = campaign("defaults") {
            axis(model, listOf("a"))
        }

        assertThat(campaign.repetitions).isEqualTo(3)
        assertThat(campaign.warmups).isEqualTo(1)
        assertThat(campaign.timeout).isEqualTo(5.minutes)
    }

    @Test
    fun `two values with the same label are rejected`() {
        val campaign = campaign("duplicate") {
            axis(Axis<Int>("parity") { if (it % 2 == 0) "even" else "odd" }, listOf(1, 3))
        }

        assertThatThrownBy { campaign.cells() }.hasMessageContaining("parity=odd")
    }

    @Test
    fun `an axis read that the cell does not have fails`() {
        val cell = campaign("missing") {
            axis(model, listOf("a"))
        }.cells().single()

        assertThatThrownBy { cell[engine] }.hasMessageContaining("engine")
    }

    @Test
    fun `campaign names are lowercase identifiers`() {
        assertThatThrownBy {
            campaign("Not A Name") {
                axis(model, listOf("a"))
            }
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
