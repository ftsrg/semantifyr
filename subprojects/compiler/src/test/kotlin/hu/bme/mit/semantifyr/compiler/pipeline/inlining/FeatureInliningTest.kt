/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import hu.bme.mit.semantifyr.compiler.pipeline.utils.serializeFormatted
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.TestModels.testModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FeatureInliningTest : InliningTestBase() {
    @Test
    fun `inline for over a superset unrolls over every subsetter`() {
        val main = inlinedMain(
            "Host",
            testModel(javaClass, "inline_for_over_superset.semantifyr"),
        )

        assertThat(main)
            .contains("root.first.hits := root.first.hits + 1")
            .contains("root.second.hits := root.second.hits + 1")
            .doesNotContain("root.other.hits")
    }

    @Test
    fun `inline for over a redefined feature unrolls over the subsetters of the redefined feature`() {
        val main = inlinedMain(
            "Host",
            testModel(javaClass, "inline_for_over_redefined_feature.semantifyr"),
        )

        assertThat(main)
            .contains("root.first.hits := root.first.hits + 1")
            .contains("root.second.hits := root.second.hits + 1")
    }

    @Test
    fun `a redefinition without subsets stays in the superset of the redefined feature`() {
        val main = inlinedMain(
            "Host",
            testModel(javaClass, "redefinition_without_subsets.semantifyr"),
        )

        assertThat(main).contains("root.holder.first.special := root.holder.first.special + 1")
    }

    @Test
    fun `a call through a feature redefined under a new name reaches the redefinition`() {
        val main = inlinedMain(
            "Host",
            testModel(javaClass, "feature_redefined_under_new_name.semantifyr"),
        )

        assertThat(main)
            .contains("root.pair.replacement.special := root.pair.replacement.special + 1")
            .doesNotContain("hits")
    }

    @Test
    fun `a reference bound in a nested block reaches the bound instance`() {
        val main = inlinedMain(
            "Host",
            testModel(javaClass, "reference_bound_in_nested_block.semantifyr"),
        )

        assertThat(main)
            .contains("root.kitchen.on := true")
            .doesNotContain("root.hall.on")
    }

    @Test
    fun `a nested block rebinds a data feature for its own instance only`() {
        val main = inlinedMain(
            "Host",
            testModel(javaClass, "nested_block_rebinds_data_feature.semantifyr"),
        )

        assertThat(main)
            .contains("root.plain.count < 2")
            .contains("root.raised.count < 3")
    }

    @Test
    fun `a derived reference collects the holders of its opposite`() {
        val main = inlinedMain(
            "Host",
            testModel(javaClass, "derived_reference.semantifyr"),
        )

        assertThat(main)
            .contains("root.firstEdge.used := true")
            .contains("root.secondEdge.used := true")
            .doesNotContain("root.thirdEdge.used")
    }

    @Test
    fun `an abstract feature redefined as a containment evaluates to the redefinition`() {
        val main = inlinedMain(
            "Host",
            testModel(javaClass, "abstract_feature_redefined_as_containment.semantifyr"),
        )

        assertThat(main).contains("root.slot.payload.hits := root.slot.payload.hits + 1")
    }

    @Test
    fun `an abstract feature filled only by subsetters evaluates to the subsetters`() {
        val main = inlinedMain(
            "Host",
            testModel(javaClass, "abstract_feature_filled_by_subsetters.semantifyr"),
        )

        assertThat(main)
            .contains("root.first.hits := root.first.hits + 1")
            .contains("root.second.hits := root.second.hits + 1")
            .doesNotContain("root.other.hits")
    }

    @Test
    fun `a call through an abstract feature filled by one subsetter reaches it`() {
        val main = inlinedMain(
            "Host",
            testModel(javaClass, "call_through_abstract_feature.semantifyr"),
        )

        assertThat(main).contains("root.slot.item.hits := root.slot.item.hits + 1")
    }

    @Test
    fun `a global reference resolves to the instance it is bound to`() {
        val main = inlinedMain(
            "Host",
            testModel(javaClass, "global_reference.semantifyr"),
        )

        assertThat(main)
            .contains("worker.done := true")
            .doesNotContain("alias")
    }

    @Test
    fun `an inline if comparing instances selects its branch statically`() {
        val main = inlinedMain(
            "Host",
            testModel(javaClass, "inline_if_comparing_instances.semantifyr"),
        )

        assertThat(main)
            .contains("root.counter := 2")
            .doesNotContain("root.counter := 1")
    }

    @Test
    fun `an inline if comparing an empty instance set with a non-empty one takes its else branch`() {
        val main = inlinedMain(
            "Host",
            testModel(javaClass, "inline_if_comparing_empty_set.semantifyr"),
        )

        assertThat(main)
            .contains("root.counter := 2")
            .doesNotContain("root.counter := 1")
    }

    private fun inlinedMain(
        className: String,
        source: String,
    ): String {
        return prepare(className, source) {
            inlineAll(it)
            serializer.serializeFormatted(it.inlinedOxsts.mainTransition)
        }
    }
}
