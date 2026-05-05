/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.transformation.xsts

import hu.bme.mit.semantifyr.xsts.lang.xsts.AssignmentOperation
import hu.bme.mit.semantifyr.xsts.lang.xsts.BooleanType
import hu.bme.mit.semantifyr.xsts.lang.xsts.ChoiceOperation
import hu.bme.mit.semantifyr.xsts.lang.xsts.IntegerType
import hu.bme.mit.semantifyr.xsts.lang.xsts.NegationOperator
import hu.bme.mit.semantifyr.xsts.lang.xsts.SequenceOperation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ThetaModelTransformerTest : ThetaTransformationTestBase() {

    @Test
    suspend fun `int variable produces a TopLevelVariableDeclaration with IntegerType`() {
        withTransformer {
            val xsts = it.transform(
                parse(
                    """
                        inlined oxsts of semantifyr::Anything
                        var x : int := 0
                        init { }
                        tran { }
                        prop { AG true }
                    """,
                ),
                testXstsUri,
            )

            assertThat(xsts.variableDeclarations).hasSize(1)
            val variable = xsts.variableDeclarations.single()
            assertThat(variable.name).isEqualTo("x")
            assertThat(variable.type).isInstanceOf(IntegerType::class.java)
        }
    }

    @Test
    suspend fun `boolean variable produces BooleanType`() {
        withTransformer {
            val xsts = it.transform(
                parse(
                    """
                        inlined oxsts of semantifyr::Anything
                        var b : bool := false
                        init { }
                        tran { }
                        prop { AG true }
                    """,
                ),
                testXstsUri,
            )

            assertThat(xsts.variableDeclarations.single().type).isInstanceOf(BooleanType::class.java)
        }
    }

    @Test
    suspend fun `assignment in tran appears as an AssignmentOperation in tran branches`() {
        withTransformer {
            val xsts = it.transform(
                parse(
                    """
                        inlined oxsts of semantifyr::Anything
                        var x : int := 0
                        init { }
                        tran { x := x + 1 }
                        prop { AG true }
                    """,
                ),
                testXstsUri,
            )

            val tranBranches = xsts.tran.branches
            assertThat(tranBranches).hasSize(1)
            val branchSteps = (tranBranches.single() as SequenceOperation).steps
            assertThat(branchSteps).hasSize(1)
            assertThat(branchSteps.single()).isInstanceOf(AssignmentOperation::class.java)
        }
    }

    @Test
    suspend fun `choice in tran produces a ChoiceOperation with two branches`() {
        withTransformer {
            val xsts = it.transform(
                parse(
                    """
                        inlined oxsts of semantifyr::Anything
                        var x : int := 0
                        init { }
                        tran { choice { x := 1 } or { x := 2 } }
                        prop { AG true }
                    """,
                ),
                testXstsUri,
            )

            val tranBranch = xsts.tran.branches.single() as SequenceOperation
            val first = tranBranch.steps.single()
            assertThat(first).isInstanceOf(ChoiceOperation::class.java)
            assertThat((first as ChoiceOperation).branches).hasSize(2)
        }
    }

    @Test
    suspend fun `AG property maps to an invariant equal to its body (no negation)`() {
        withTransformer {
            val xsts = it.transform(
                parse(
                    """
                        inlined oxsts of semantifyr::Anything
                        var x : int := 0
                        init { }
                        tran { }
                        prop { AG (x == 5) }
                    """,
                ),
                testXstsUri,
            )

            assertThat(xsts.property.invariant).isNotInstanceOf(NegationOperator::class.java)
        }
    }

    @Test
    suspend fun `EF property maps to a NegationOperator over the body`() {
        withTransformer {
            val xsts = it.transform(
                parse(
                    """
                        inlined oxsts of semantifyr::Anything
                        var x : int := 0
                        init { }
                        tran { }
                        prop { EF (x == 5) }
                    """,
                ),
                testXstsUri,
            )

            assertThat(xsts.property.invariant).isInstanceOf(NegationOperator::class.java)
        }
    }
}
