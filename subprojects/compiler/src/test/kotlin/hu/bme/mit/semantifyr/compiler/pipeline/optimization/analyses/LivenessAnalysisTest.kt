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
                var observed : int := 0
                init {
                }
                tran {
                }
                prop {
                    AG (observed == 1)
                }
            """,
        )

        val observed = inlined.varNamed("observed")
        assertThat(result.isRead(observed)).isTrue
    }

    @Test
    fun `variable with no reads is not marked read`() {
        val (inlined, result) = runLiveness(
            """
                inlined oxsts of semantifyr::Anything
                var observed : int := 0
                var written : int := 0
                init {
                }
                tran {
                    written := 1
                }
                prop {
                    AG (observed == 1)
                }
            """,
        )

        val written = inlined.varNamed("written")
        assertThat(result.isRead(written)).isFalse
        assertThat(result.isAssigned(written)).isTrue
    }

    @Test
    fun `variable with both assignment and havoc has both in the assignments set`() {
        val (inlined, result) = runLiveness(
            """
                inlined oxsts of semantifyr::Anything
                var value : int := 0
                init {
                }
                tran {
                    havoc(value)
                    value := 1
                }
                prop {
                    AG (value == 1)
                }
            """,
        )

        val value = inlined.varNamed("value")
        assertThat(result.assignmentCount(value))
            .`as`("assignments map must include both the havoc and the assignment")
            .isEqualTo(2)
    }

    @Test
    fun `guard expression reads count as reads`() {
        val (inlined, result) = runLiveness(
            """
                inlined oxsts of semantifyr::Anything
                var guard : int := 0
                var written : int := 0
                init {
                }
                tran {
                    assume(guard == 0)
                    written := 1
                }
                prop {
                    AG true
                }
            """,
        )

        val guard = inlined.varNamed("guard")
        assertThat(result.isRead(guard))
            .`as`("'guard' is read inside the assume guard - must be marked read")
            .isTrue
    }

    private fun InlinedOxsts.varNamed(name: String): VariableDeclaration {
        return eAllOfType<VariableDeclaration>().firstOrNull { it.name == name }
            ?: error("No variable named '$name' in inlined oxsts")
    }
}
