/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.utils

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.InlinedOxstsParseHelper
import hu.bme.mit.semantifyr.oxsts.lang.utils.SourceLocation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

@InjectWithOxsts
class SourceErrorTest {
    @Inject
    private lateinit var parseHelper: InlinedOxstsParseHelper

    @Test
    fun `sourceError includes file name and line of the targeted EObject`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var first : int := 0
                var second : int := 0
                init {
                }
                tran {
                }
                prop {
                    AG true
                }
            """.trimIndent(),
        )

        val secondVariable = inlined.eAllOfType<VariableDeclaration>().first {
            it.name == "second"
        }

        assertThatThrownBy {
            sourceError(secondVariable, "boom")
        }.hasMessageContaining("boom")
            .hasMessageMatching(".*:\\d+:.*boom")
    }

    @Test
    fun `SourceLocation_prefixFor carries line number that matches the source`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var first : int := 0
                var second : int := 0
                init {
                }
                tran {
                }
                prop {
                    AG true
                }
            """.trimIndent(),
        )

        val firstVariable = inlined.eAllOfType<VariableDeclaration>().first {
            it.name == "first"
        }
        val secondVariable = inlined.eAllOfType<VariableDeclaration>().first {
            it.name == "second"
        }

        val firstPrefix = SourceLocation.prefixFor(firstVariable)
        val secondPrefix = SourceLocation.prefixFor(secondVariable)

        assertThat(firstPrefix).matches(".*:\\d+: ")
        assertThat(secondPrefix).matches(".*:\\d+: ")
        assertThat(firstPrefix).isNotEqualTo(secondPrefix)
    }
}
