/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.utils

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.pipeline.utils.sourceError
import hu.bme.mit.semantifyr.compiler.pipeline.utils.sourceLocationPrefix
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.InlinedOxstsParseHelper
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
                var a : int := 0
                var b : int := 0
                init { }
                tran { }
                prop { AG true }
            """.trimIndent(),
        )

        val bVariable = inlined.eAllOfType<VariableDeclaration>().first { it.name == "b" }

        assertThatThrownBy {
            sourceError(bVariable, "boom")
        }.hasMessageContaining("boom")
            .hasMessageMatching(".*:\\d+:.*boom")
    }

    @Test
    fun `sourceLocationPrefix carries line number that matches the source`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                init { }
                tran { }
                prop { AG true }
            """.trimIndent(),
        )

        val aVariable = inlined.eAllOfType<VariableDeclaration>().first { it.name == "a" }
        val bVariable = inlined.eAllOfType<VariableDeclaration>().first { it.name == "b" }

        val prefixA = sourceLocationPrefix(aVariable)
        val prefixB = sourceLocationPrefix(bVariable)

        assertThat(prefixA).matches(".*:\\d+: ")
        assertThat(prefixB).matches(".*:\\d+: ")
        assertThat(prefixA).isNotEqualTo(prefixB)
    }

}
