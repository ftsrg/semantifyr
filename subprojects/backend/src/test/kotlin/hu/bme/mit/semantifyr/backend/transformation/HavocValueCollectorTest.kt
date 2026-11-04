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
        val havoced = variable(
            """
                var havoced : int
                init {
                }
                tran {
                }
                prop {
                    AG !(havoced == 3) || havoced > 3
                }
            """,
        )

        assertThat(HavocValueCollector().valuesFor(havoced)).contains(2, 3, 4)
    }

    @Test
    fun `candidates are complete for an integer read only in comparisons with literals`() {
        val havoced = variable(
            """
                var havoced : int
                init {
                }
                tran {
                    havoc(havoced)
                    havoced := 4
                }
                prop {
                    AG (havoced != 3 && havoced <= 7)
                }
            """,
        )

        assertThat(HavocValueCollector().isComplete(havoced)).isTrue
    }

    @Test
    fun `candidates are not complete for an integer read in arithmetic or compared with a variable`() {
        val arithmetic = variable(
            """
                var havoced : int
                init {
                }
                tran {
                }
                prop {
                    AG havoced + 1 < 5
                }
            """,
        )
        val copied = variable(
            """
                var havoced : int
                var copy : int := 0
                init {
                }
                tran {
                    copy := havoced
                }
                prop {
                    AG copy < 5
                }
            """,
        )
        val compared = variable(
            """
                var havoced : int
                var bound : int := 0
                init {
                }
                tran {
                }
                prop {
                    AG havoced < bound
                }
            """,
        )

        assertThat(HavocValueCollector().isComplete(arithmetic)).isFalse
        assertThat(HavocValueCollector().isComplete(copied)).isFalse
        assertThat(HavocValueCollector().isComplete(compared)).isFalse
    }
}
