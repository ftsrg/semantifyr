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
                var value : int := 7
                init {
                    value := 7
                }
                tran {
                }
                prop {
                    AG (value == 7)
                }
            """,
        )

        val value = inlined.varNamed("value")
        assertThat(result.isConstant(value)).isTrue
    }

    @Test
    fun `variable with differing writes is not constant`() {
        val (inlined, result) = runConstantValue(
            """
                inlined oxsts of semantifyr::Anything
                var value : int := 0
                init {
                }
                tran {
                    choice {
                        value := 1
                    } or {
                        value := 2
                    }
                }
                prop {
                    AG (value != 3)
                }
            """,
        )

        val value = inlined.varNamed("value")
        assertThat(result.isConstant(value)).isFalse
    }

    @Test
    fun `havoced variable is not constant even when non-havoc writes agree`() {
        val (inlined, result) = runConstantValue(
            """
                inlined oxsts of semantifyr::Anything
                var value : int := 7
                init {
                    value := 7
                }
                tran {
                    havoc(value)
                }
                prop {
                    AG (value == 7)
                }
            """,
        )

        val value = inlined.varNamed("value")
        assertThat(result.isConstant(value))
            .`as`("havoc introduces non-determinism - variable is not constant")
            .isFalse
    }

    @Test
    fun `variable with only an initializer is not reported constant here`() {
        val (inlined, result) = runConstantValue(
            """
                inlined oxsts of semantifyr::Anything
                var value : int := 7
                init {
                }
                tran {
                }
                prop {
                    AG (value == 7)
                }
            """,
        )

        val value = inlined.varNamed("value")
        assertThat(result.isConstant(value)).isFalse
    }

    @Test
    fun `a declaration that init overwrites is unobserved, so the property reads a constant`() {
        val (inlined, result) = runConstantValue(
            """
                inlined oxsts of semantifyr::Anything
                var overwritten : int := 0
                init {
                    overwritten := 3
                }
                tran {
                }
                prop {
                    AG (overwritten == 3)
                }
            """,
        )

        assertThat(result.isConstant(inlined.varNamed("overwritten")))
            .`as`("the declaration valuation is not a state, so the property never observes the declared 0")
            .isTrue
    }

    @Test
    fun `a declaration that init writes only conditionally keeps the variable from being constant`() {
        val (inlined, result) = runConstantValue(
            """
                inlined oxsts of semantifyr::Anything
                var overwritten : int := 0
                var copy : int := 3
                init {
                    choice {
                        overwritten := 3
                    } or {
                    }
                }
                tran {
                    copy := overwritten
                }
                prop {
                    AG (copy == 3)
                }
            """,
        )

        assertThat(result.isConstant(inlined.varNamed("overwritten"))).isFalse
    }

    @Test
    fun `a declaration that only tran overwrites keeps the variable from being constant`() {
        val (inlined, result) = runConstantValue(
            """
                inlined oxsts of semantifyr::Anything
                var overwritten : int := 0
                var copy : int := 3
                init {
                }
                tran {
                    copy := overwritten
                    overwritten := 3
                }
                prop {
                    AG (copy == 3)
                }
            """,
        )

        assertThat(result.isConstant(inlined.varNamed("overwritten"))).isFalse
    }

    @Test
    fun `a declaration that init reads before overwriting it keeps the variable from being constant`() {
        val (inlined, result) = runConstantValue(
            """
                inlined oxsts of semantifyr::Anything
                var overwritten : int := 0
                var copy : int := 3
                init {
                    copy := overwritten
                    overwritten := 3
                }
                tran {
                }
                prop {
                    AG (copy == 3)
                }
            """,
        )

        assertThat(result.isConstant(inlined.varNamed("overwritten"))).isFalse
    }

    @Test
    fun `a declaration that init must overwrite and only tran reads leaves the variable constant`() {
        val (inlined, result) = runConstantValue(
            """
                inlined oxsts of semantifyr::Anything
                var overwritten : int := 0
                var copy : int := 3
                init {
                    choice {
                        overwritten := 3
                    } or {
                        overwritten := 3
                    }
                }
                tran {
                    copy := overwritten
                }
                prop {
                    AG (copy == 3)
                }
            """,
        )

        assertThat(result.isConstant(inlined.varNamed("overwritten"))).isTrue
    }

    private fun InlinedOxsts.varNamed(name: String): VariableDeclaration {
        return eAllOfType<VariableDeclaration>().firstOrNull { it.name == name }
            ?: error("No variable named '$name' in inlined oxsts")
    }
}
