/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LivenessAnalysisTest : AnalysisTestBase() {

    @Test
    fun `variable read by the property is marked read`() {
        val (inlined, result) = runLiveness(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                init { }
                tran { }
                prop { AG (a == 1) }
            """,
        )

        val a = inlined.varNamed("a")
        assertThat(result.isRead(a)).isTrue
    }

    @Test
    fun `variable with no reads is not marked read`() {
        val (inlined, result) = runLiveness(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                init { }
                tran { b := 1 }
                prop { AG (a == 1) }
            """,
        )

        val b = inlined.varNamed("b")
        assertThat(result.isRead(b)).isFalse
        assertThat(result.isAssigned(b)).isTrue
    }

    // Regression: assignments and havocs used to be merged via Map.plus in the
    // assignment index, which silently dropped one group when a variable had
    // both. The fix concatenates writes before groupBy; this test guards it.
    @Test
    fun `variable with both assignment and havoc has both in the assignments set`() {
        val (inlined, result) = runLiveness(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                init { }
                tran {
                    havoc(a)
                    a := 1
                }
                prop { AG (a == 1) }
            """,
        )

        val a = inlined.varNamed("a")
        assertThat(result.assignmentCount(a))
            .`as`("assignments map must include both the havoc and the assignment")
            .isEqualTo(2)
    }

    @Test
    fun `guard expression reads count as reads`() {
        val (inlined, result) = runLiveness(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                init { }
                tran {
                    assume(a == 0)
                    b := 1
                }
                prop { AG true }
            """,
        )

        val a = inlined.varNamed("a")
        assertThat(result.isRead(a))
            .`as`("'a' is read inside the assume guard - must be marked read")
            .isTrue
    }

    private fun InlinedOxsts.varNamed(name: String): VariableDeclaration {
        return eAllOfType<VariableDeclaration>().firstOrNull { it.name == name }
            ?: error("No variable named '$name' in inlined oxsts")
    }
}
