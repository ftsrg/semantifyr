/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NuxmvCapabilityCheckerTest : NuxmvTransformationTestBase() {

    private val colors = """
        package test::colors
        enum Color {
            Red,
            Green,
        }
    """

    private fun unsupported(body: String): List<String> {
        val inlinedOxsts = parse("inlined oxsts of semantifyr::Anything\n${body.trimIndent()}", colors)
        return capabilityChecker().check(inlinedOxsts).map {
            it.description
        }
    }

    @Test
    fun `a model of supported constructs passes`() {
        val unsupported = unsupported(
            """
                var counter : int := 0
                var flag : bool := false
                var color : test::colors::Color := test::colors::Color::Red
                init {
                    havoc(flag)
                }
                tran {
                    choice {
                        assume(counter < 3)
                        counter := counter + 1
                    } or {
                        if (flag) {
                            color := test::colors::Color::Green
                        }
                    }
                }
                prop {
                    AG (counter <= 3 && (if flag then true else color == test::colors::Color::Red))
                }
            """,
        )

        assertThat(unsupported).isEmpty()
    }

    @Test
    fun `every unsupported construct is reported`() {
        val unsupported = unsupported(
            """
                var ratio : real := 0.5
                var anything : any := 0
                var text : string := "text"
                var counter : int := 0
                init {
                }
                tran {
                    for (index in 0..2) {
                        counter := counter + index
                    }
                }
                prop {
                    AG true
                }
            """,
        )

        assertThat(unsupported).containsExactlyInAnyOrder(
            "variables of type real",
            "variables of type any",
            "variables of type string",
            "string literals",
            "for-operations",
        )
    }
}
