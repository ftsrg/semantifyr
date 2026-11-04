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
    fun `uninitialized variable read by the property is not constant although init writes it`() {
        val (inlined, result) = runConstantValue(
            """
                inlined oxsts of semantifyr::Anything
                var uninitialized : int
                init {
                    uninitialized := 3
                }
                tran {
                }
                prop {
                    AG (uninitialized == 3)
                }
            """,
        )

        assertThat(result.isConstant(inlined.varNamed("uninitialized")))
            .`as`("the property is checked in the pre-init state, where uninitialized is havoced")
            .isFalse
    }

    @Test
    fun `uninitialized variable written only conditionally in init is not constant`() {
        val (inlined, result) = runConstantValue(
            """
                inlined oxsts of semantifyr::Anything
                var uninitialized : int
                var copy : int := 3
                init {
                    choice {
                        uninitialized := 3
                    } or {
                    }
                }
                tran {
                    copy := uninitialized
                }
                prop {
                    AG (copy == 3)
                }
            """,
        )

        assertThat(result.isConstant(inlined.varNamed("uninitialized"))).isFalse
    }

    @Test
    fun `uninitialized variable written only in tran is not constant`() {
        val (inlined, result) = runConstantValue(
            """
                inlined oxsts of semantifyr::Anything
                var uninitialized : int
                var copy : int := 3
                init {
                }
                tran {
                    copy := uninitialized
                    uninitialized := 3
                }
                prop {
                    AG (copy == 3)
                }
            """,
        )

        assertThat(result.isConstant(inlined.varNamed("uninitialized"))).isFalse
    }

    @Test
    fun `uninitialized variable read in init is not constant`() {
        val (inlined, result) = runConstantValue(
            """
                inlined oxsts of semantifyr::Anything
                var uninitialized : int
                var copy : int := 3
                init {
                    copy := uninitialized
                    uninitialized := 3
                }
                tran {
                }
                prop {
                    AG (copy == 3)
                }
            """,
        )

        assertThat(result.isConstant(inlined.varNamed("uninitialized"))).isFalse
    }

    @Test
    fun `uninitialized variable that init must write and only tran reads is constant`() {
        val (inlined, result) = runConstantValue(
            """
                inlined oxsts of semantifyr::Anything
                var uninitialized : int
                var copy : int := 3
                init {
                    choice {
                        uninitialized := 3
                    } or {
                        uninitialized := 3
                    }
                }
                tran {
                    copy := uninitialized
                }
                prop {
                    AG (copy == 3)
                }
            """,
        )

        assertThat(result.isConstant(inlined.varNamed("uninitialized"))).isTrue
    }

    private fun InlinedOxsts.varNamed(name: String): VariableDeclaration {
        return eAllOfType<VariableDeclaration>().firstOrNull { it.name == name }
            ?: error("No variable named '$name' in inlined oxsts")
    }
}
