/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UppaalModelTransformerTest : UppaalTransformationTestBase() {

    @Test
    suspend fun `int variable produces an int declaration with initial value`() {
        withTransformer {
            val artifacts = it.transform(
                parse(
                    """
                        inlined oxsts of semantifyr::Anything
                        var x : int := 0
                        init { }
                        tran { }
                        prop { AG true }
                    """,
                ),
            )

            assertThat(artifacts.modelXml).contains("<nta>")
            assertThat(artifacts.modelXml).contains("int x = 0;")
            assertThat(artifacts.modelXml).contains("<template>")
        }
    }

    @Test
    suspend fun `boolean variable uses bool type`() {
        withTransformer {
            val artifacts = it.transform(
                parse(
                    """
                        inlined oxsts of semantifyr::Anything
                        var b : bool := false
                        init { }
                        tran { }
                        prop { AG true }
                    """,
                ),
            )

            assertThat(artifacts.modelXml).contains("bool b = false;")
        }
    }

    @Test
    suspend fun `assignment edge in tran emits the matching label`() {
        withTransformer {
            val artifacts = it.transform(
                parse(
                    """
                        inlined oxsts of semantifyr::Anything
                        var x : int := 0
                        init { }
                        tran { x := x + 1 }
                        prop { AG true }
                    """,
                ),
            )

            assertThat(artifacts.modelXml).contains("<label kind=\"assignment\">x = (x + 1)</label>")
        }
    }

    @Test
    suspend fun `committed Start and stable Running locations are present`() {
        withTransformer {
            val artifacts = it.transform(
                parse(
                    """
                        inlined oxsts of semantifyr::Anything
                        var x : int := 0
                        init { }
                        tran { }
                        prop { AG true }
                    """,
                ),
            )

            assertThat(artifacts.modelXml).contains("<name>Start</name>")
            assertThat(artifacts.modelXml).contains("<name>Running</name>")
            assertThat(artifacts.modelXml).contains("<committed/>")
            assertThat(artifacts.modelXml).contains("<init ref=\"start\"/>")
        }
    }

    @Test
    suspend fun `EF property emits E-diamond stable-and-body`() {
        withTransformer {
            val artifacts = it.transform(
                parse(
                    """
                        inlined oxsts of semantifyr::Anything
                        var x : int := 0
                        init { }
                        tran { }
                        prop { EF (x == 5) }
                    """,
                ),
            )

            assertThat(artifacts.query).isEqualTo("E<> (Model.Running and ((x == 5)))")
        }
    }

    @Test
    suspend fun `AG property emits A-box stable-imply-body`() {
        withTransformer {
            val artifacts = it.transform(
                parse(
                    """
                        inlined oxsts of semantifyr::Anything
                        var x : int := 0
                        init { }
                        tran { }
                        prop { AG (x == 5) }
                    """,
                ),
            )

            assertThat(artifacts.query).isEqualTo("A[] (Model.Running imply ((x == 5)))")
        }
    }
}
