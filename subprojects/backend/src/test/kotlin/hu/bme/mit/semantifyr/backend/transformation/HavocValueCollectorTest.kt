/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.BackendTestModels
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@InjectWithOxsts
class HavocValueCollectorTest {

    @Inject
    private lateinit var models: BackendTestModels

    private fun variable(body: String): VariableDeclaration {
        return models.parse("inlined oxsts of semantifyr::Anything\n${body.trimIndent()}").variables.first()
    }

    @Test
    fun `an equality has a candidate on both sides of its literal`() {
        val x = variable(
            """
                var x : int
                init { }
                tran { }
                prop { AG !(x == 3) || x > 3 }
            """,
        )

        assertThat(HavocValueCollector().valuesFor(x)).contains(2, 3, 4)
    }

    @Test
    fun `candidates are complete for an integer read only in comparisons with literals`() {
        val x = variable(
            """
                var x : int
                init { }
                tran { havoc(x) x := 4 }
                prop { AG (x != 3 && x <= 7) }
            """,
        )

        assertThat(HavocValueCollector().isComplete(x)).isTrue
    }

    @Test
    fun `candidates are not complete for an integer read in arithmetic or compared with a variable`() {
        val arithmetic = variable(
            """
                var x : int
                init { }
                tran { }
                prop { AG x + 1 < 5 }
            """,
        )
        val copied = variable(
            """
                var x : int
                var y : int := 0
                init { }
                tran { y := x }
                prop { AG y < 5 }
            """,
        )
        val compared = variable(
            """
                var x : int
                var y : int := 0
                init { }
                tran { }
                prop { AG x < y }
            """,
        )

        assertThat(HavocValueCollector().isComplete(arithmetic)).isFalse
        assertThat(HavocValueCollector().isComplete(copied)).isFalse
        assertThat(HavocValueCollector().isComplete(compared)).isFalse
    }
}
