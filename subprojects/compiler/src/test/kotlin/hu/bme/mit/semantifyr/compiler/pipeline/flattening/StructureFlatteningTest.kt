/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.flattening

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.TestModels.testModel
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class StructureFlatteningTest : FlatteningTestBase() {
    @Test
    fun `a shared containment is one instance in every holder`() {
        val flattened = flatten(
            "Host",
            testModel(javaClass, "shared_containment.semantifyr"),
        )

        assertThat(
            flattened.inlinedOxsts.variables.map {
                it.name
            },
        ).hasSize(3)
        assertThat(serialize(flattened.inlinedOxsts.property))
            .doesNotContain("secondUser")
    }

    @Test
    fun `a container feature navigates to the instance that contains it`() {
        assertFlattenedModel(
            "Host",
            testModel(javaClass, "container_feature.semantifyr"),
            $$$$"""
                init {
                }

                tran {
                    assume('$$root$$parent$$generation' < 3)
                    '$$root$$parent$$generation' := '$$root$$parent$$generation' + 1
                }

                prop {
                    EF '$$root$$parent$$generation' == 3
                }
            """,
        )
    }

    @Test
    fun `a member read through an absent optional instance fails compilation`() {
        assertThatThrownBy {
            flatten(
                "Host",
                """
                    package flattening::tests
                    class Leaf {
                        var count: int := 0
                    }
                    @VerificationCase
                    class Host {
                        contains leaf: Leaf[0..1]
                        redefine prop {
                            return AG leaf?.count == 0
                        }
                    }
                """,
            )
        }.hasMessageContaining("This expression is not evaluable to a single instance!")
    }

    @Test
    fun `branches of the main and the initial transition stay alternatives`() {
        val flattened = flatten(
            "Host",
            testModel(javaClass, "alternative_branches.semantifyr"),
        )

        assertThat(flattened.inlinedOxsts.initTransition.eAllOfType<ChoiceOperation>().single().branches).hasSize(2)
        assertThat(flattened.inlinedOxsts.mainTransition.eAllOfType<ChoiceOperation>().single().branches).hasSize(2)
    }
}
