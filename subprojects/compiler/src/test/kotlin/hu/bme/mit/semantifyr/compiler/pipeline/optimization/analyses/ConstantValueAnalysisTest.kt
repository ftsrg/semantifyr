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

class ConstantValueAnalysisTest : AnalysisTestBase() {
    @Test
    fun `variable whose initializer and only write agree is considered constant`() {
        val (inlined, result) = runConstantValue(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 7
                init { a := 7 }
                tran { }
                prop { AG (a == 7) }
            """,
        )

        val a = inlined.varNamed("a")
        assertThat(result.isConstant(a)).isTrue
    }

    @Test
    fun `variable with differing writes is not constant`() {
        val (inlined, result) = runConstantValue(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                init { }
                tran {
                    choice { a := 1 } or { a := 2 }
                }
                prop { AG (a != 3) }
            """,
        )

        val a = inlined.varNamed("a")
        assertThat(result.isConstant(a)).isFalse
    }

    @Test
    fun `havoced variable is not constant even when non-havoc writes agree`() {
        val (inlined, result) = runConstantValue(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 7
                init { a := 7 }
                tran { havoc(a) }
                prop { AG (a == 7) }
            """,
        )

        val a = inlined.varNamed("a")
        assertThat(result.isConstant(a))
            .`as`("havoc introduces non-determinism - variable is not constant")
            .isFalse
    }

    @Test
    fun `variable with only an initializer is not reported constant here`() {
        val (inlined, result) = runConstantValue(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 7
                init { }
                tran { }
                prop { AG (a == 7) }
            """,
        )

        val a = inlined.varNamed("a")
        assertThat(result.isConstant(a)).isFalse
    }

    private fun InlinedOxsts.varNamed(name: String): VariableDeclaration {
        return eAllOfType<VariableDeclaration>().firstOrNull { it.name == name }
            ?: error("No variable named '$name' in inlined oxsts")
    }
}
