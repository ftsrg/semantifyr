/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ConstantValueAnalysis
import org.junit.jupiter.api.Test

class ConstantVariableSubstitutionPassTest : PassTestBase() {
    @Test
    fun `variable whose initializer and only assignment agree is substituted`() = assertPassTransforms(
        source = """
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
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var value : int := 7
            init {
                value := 7
            }
            tran {
            }
            prop {
                AG (7 == 7)
            }
        """,
        analysisClasses = listOf(ConstantValueAnalysis::class.java),
    ) {
        it.getInstance(ConstantVariableSubstitutionPass::class.java)
    }

    @Test
    fun `havoced variable is not treated as constant`() = assertPassTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var value : int := 0
            init {
            }
            tran {
                havoc(value)
                value := 7
            }
            prop {
                AG (value == 7)
            }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var value : int := 0
            init {
            }
            tran {
                havoc(value)
                value := 7
            }
            prop {
                AG (value == 7)
            }
        """,
        analysisClasses = listOf(ConstantValueAnalysis::class.java),
    ) {
        it.getInstance(ConstantVariableSubstitutionPass::class.java)
    }

    @Test
    fun `variable with differing writes is not treated as constant`() = assertPassTransforms(
        source = """
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
        expectedSource = """
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
        analysisClasses = listOf(ConstantValueAnalysis::class.java),
    ) {
        it.getInstance(ConstantVariableSubstitutionPass::class.java)
    }
}
