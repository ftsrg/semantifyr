/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NuxmvModelTransformerTest : NuxmvTransformationTestBase() {

    @Test
    suspend fun `int variable produces integer VAR with seeded initial constraint`() {
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

            assertThat(artifacts.smv).contains("MODULE main")
            assertThat(artifacts.smv).contains("x : integer;")
            assertThat(artifacts.smv).contains("x__init_seed_0 : integer;")
            assertThat(artifacts.smv).contains("INIT")
            assertThat(artifacts.smv).contains("x__init_seed_0 = 0")
            assertThat(artifacts.smv).contains("x = x__init_seed_0")
            assertThat(artifacts.smv).contains("TRANS")
            assertThat(artifacts.smv).contains("next(x) = x")
        }
    }

    @Test
    suspend fun `boolean variable produces boolean VAR with FALSE seeded constraint`() {
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

            assertThat(artifacts.smv).contains("b : boolean;")
            assertThat(artifacts.smv).contains("b__init_seed_0 = FALSE")
        }
    }

    @Test
    suspend fun `assignment in tran allocates a primed variable and binds next-equation`() {
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

            assertThat(artifacts.smv).contains("x__tran_b0_1 : integer;")
            assertThat(artifacts.smv).contains("x__tran_b0_1 = (x + 1)")
            assertThat(artifacts.smv).contains("next(x) = x__tran_b0_1")
        }
    }

    @Test
    suspend fun `choice in tran emits an IVAR and case-key disjunction`() {
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

            assertThat(artifacts.smv).contains("IVAR")
            assertThat(artifacts.smv).contains("nondet_tran_0 : 0..1")
            assertThat(artifacts.smv).contains("nondet_tran_0 = 0")
            assertThat(artifacts.smv).contains("nondet_tran_0 = 1")
            assertThat(artifacts.smv).contains("case")
            assertThat(artifacts.smv).contains("esac")
        }
    }

    @Test
    suspend fun `choice in init emits a FROZENVAR rather than an IVAR`() {
        withTransformer {
            val artifacts = it.transform(
                parse(
                    """
                        inlined oxsts of semantifyr::Anything
                        var x : int := 0
                        init { choice { x := 1 } or { x := 2 } }
                        tran { }
                        prop { AG true }
                    """,
                ),
            )

            assertThat(artifacts.smv).contains("FROZENVAR")
            assertThat(artifacts.smv).contains("nondet_init_0 : 0..1")
            assertThat(artifacts.smv).doesNotContain("IVAR")
        }
    }

    @Test
    suspend fun `EF property is wrapped with negation and inverts the verdict`() {
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
            assertThat(artifacts.property.invariant).isEqualTo("!((x = 5))")
        }
    }

    @Test
    suspend fun `AG property keeps the verdict and emits the body unchanged`() {
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
            assertThat(artifacts.property.invariant).isEqualTo("(x = 5)")
        }
    }
}
