/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.transformation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpinModelTransformerTest : SpinTransformationTestBase() {

    @Test
    suspend fun `int variable produces a typed global with initializer`() {
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

            assertThat(artifacts.promela).contains("int x = 0;")
            assertThat(artifacts.promela).contains("bool $SPIN_STABLE_FLAG = false;")
            assertThat(artifacts.promela).contains("init {")
            assertThat(artifacts.promela).contains("ltl p {")
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

            assertThat(artifacts.promela).contains("bool b = false;")
        }
    }

    @Test
    suspend fun `assignment in tran emits a Promela assignment line`() {
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

            assertThat(artifacts.promela).contains("do")
            assertThat(artifacts.promela).contains("x = (x + 1);")
            assertThat(artifacts.promela).contains("od;")
        }
    }

    @Test
    suspend fun `choice in tran emits an if-fi block with stable-flag toggling`() {
        withTransformer {
            val artifacts = it.transform(
                parse(
                    """
                        inlined oxsts of semantifyr::Anything
                        var x : int := 0
                        init { }
                        tran { choice { x := 1 } or { x := 2 } }
                        prop { AG true }
                    """,
                ),
            )

            assertThat(artifacts.promela).contains("if")
            assertThat(artifacts.promela).contains("x = 1;")
            assertThat(artifacts.promela).contains("x = 2;")
            assertThat(artifacts.promela).contains("$SPIN_STABLE_FLAG = false;")
            assertThat(artifacts.promela).contains("$SPIN_STABLE_FLAG = true;")
        }
    }

    @Test
    suspend fun `EF property uses inverted-verdict LTL form`() {
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

            assertThat(artifacts.property.invertVerdict).isTrue()
            assertThat(artifacts.property.ltl).isEqualTo("[] !($SPIN_STABLE_FLAG && ((x == 5)))")
        }
    }

    @Test
    suspend fun `AG property uses non-inverted LTL form`() {
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

            assertThat(artifacts.property.invertVerdict).isFalse()
            assertThat(artifacts.property.ltl).isEqualTo("[] (!$SPIN_STABLE_FLAG || ((x == 5)))")
        }
    }
}
