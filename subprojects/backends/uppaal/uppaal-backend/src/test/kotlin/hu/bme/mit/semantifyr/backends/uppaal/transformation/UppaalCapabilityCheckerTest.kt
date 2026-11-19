/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UppaalCapabilityCheckerTest : UppaalTransformationTestBase() {

    private val colors = """
        package test::colors
        enum Color {
            Red,
            Green,
        }
    """

    private fun unsupported(
        body: String,
        checkerClass: Class<out UppaalCapabilityChecker> = UppaalCapabilityChecker::class.java,
    ): List<String> {
        val inlinedOxsts = parse("inlined oxsts of semantifyr::Anything\n${body.trimIndent()}", colors)
        return withCapabilityChecker(checkerClass) {
            it.check(inlinedOxsts)
        }.map {
            it.description
        }
    }

    private fun overApproximationUnsupported(body: String): List<String> {
        return unsupported(body, UppaalOverApproximationCapabilityChecker::class.java)
    }

    @Test
    fun `the over-approximation refuses a havoced integer read other than in comparisons with literals`() {
        val havoc = """
            var counter : int := 0
            var copy : int := 0
            init {
            }
            tran {
                havoc(counter)
                copy := counter + 1
            }
            prop {
                AG copy < 5
            }
        """
        val initial = """
            var counter : int := 0
            init {
                havoc(counter)
            }
            tran {
            }
            prop {
                AG counter * 2 < 5
            }
        """
        val local = """
            var copy : int := 0
            init {
            }
            tran {
                var temporary : int := 0
                havoc(temporary)
                copy := temporary
            }
            prop {
                AG copy < 5
            }
        """

        assertThat(overApproximationUnsupported(havoc)).containsExactly("havoced integers read other than in comparisons with literals")
        assertThat(overApproximationUnsupported(initial)).contains("havoced integers read other than in comparisons with literals")
        assertThat(overApproximationUnsupported(local)).containsExactly("havoced integers read other than in comparisons with literals")
        assertThat(unsupported(havoc)).isEmpty()
    }

    @Test
    fun `the over-approximation accepts havoced integers compared only with literals and havoced booleans`() {
        val unsupported = overApproximationUnsupported(
            """
                var counter : int := 0
                var flag : bool := false
                init {
                }
                tran {
                    havoc(counter)
                    havoc(flag)
                }
                prop {
                    AG (counter == 3 || counter < 0 || flag)
                }
            """,
        )

        assertThat(unsupported).isEmpty()
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
                var elapsed : clock := 0
                var counter : int := 0
                init {
                }
                tran {
                    elapsed := 0
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
            "for-operations",
        )
    }

    private fun clockModel(tran: String): String {
        return """
            var elapsed : clock := 0
            var flag : bool := false
            var late : bool := false
            var count : int := 0
            init {
            }
            tran {
            ${tran.trimIndent().replace("\n", "\n            ")}
            }
            prop {
                AG !late
            }
        """
    }

    @Test
    fun `a clock read outside the condition of an assumption or an if is reported`() {
        val stored = clockModel(
            """
                assume(elapsed <= 1)
                late := elapsed >= 2
            """,
        )
        val conditionalValue = clockModel(
            """
                assume(elapsed <= 1)
                count := if (elapsed >= 1) then 1 else 0
            """,
        )
        val localInitializer = clockModel(
            """
                var early : bool := elapsed <= 1
                late := !early
            """,
        )
        val comparedWithBoolean = clockModel(
            """
                assume((elapsed >= 2) == flag)
                late := true
            """,
        )
        val conditionalValueInAssumption = clockModel(
            """
                assume(count == (if (elapsed >= 1) then 1 else 0))
                late := true
            """,
        )

        val description = "clocks read outside the conditions of assumptions and ifs"
        assertThat(unsupported(stored)).containsExactly(description)
        assertThat(unsupported(conditionalValue)).containsExactly(description)
        assertThat(unsupported(localInitializer)).containsExactly(description)
        assertThat(unsupported(comparedWithBoolean)).containsExactly(description)
        assertThat(unsupported(conditionalValueInAssumption)).containsExactly(description)
    }

    @Test
    fun `clock tests in the conditions of assumptions and ifs pass`() {
        val unsupported = unsupported(
            clockModel(
                """
                    assume(elapsed <= 5 && !(elapsed < 2))
                    assume(if (flag) then elapsed <= 3 else elapsed >= 4)
                    if (elapsed >= 4 || flag) {
                        late := true
                    }
                    elapsed := 0
                """,
            ),
        )

        assertThat(unsupported).isEmpty()
    }

    @Test
    fun `a clock bound beyond the magnitude verifyta takes is reported`() {
        val atLimit = clockModel(
            """
                assume(elapsed <= 1073741822)
                assume(elapsed >= -1073741822)
            """,
        )
        val aboveLimit = clockModel(
            """
                assume(elapsed >= 1073741823)
            """,
        )
        val belowLimit = clockModel(
            """
                assume(elapsed >= -1073741823)
            """,
        )

        assertThat(unsupported(atLimit)).isEmpty()
        assertThat(unsupported(aboveLimit)).containsExactly("clock bounds beyond 1073741822")
        assertThat(unsupported(belowLimit)).containsExactly("clock bounds beyond 1073741822")
    }

    @Test
    fun `arithmetic over an unbounded integer is reported only when the property depends on it`() {
        val unsupported = unsupported(
            """
                var counted : int := 1
                var ignored : int := 1
                init {
                }
                tran {
                    counted := counted * 3
                    ignored := ignored * 3
                }
                prop {
                    AG counted > 0
                }
            """,
        )

        assertThat(unsupported).containsExactly("arithmetic that may leave the 32-bit range")
    }

    @Test
    fun `incrementing or doubling an unbounded integer stays within 32 bits under the 31-bit range`() {
        val unsupported = unsupported(
            """
                var counted : int := 0
                var doubled : int := 1
                init {
                }
                tran {
                    counted := counted + 1
                    doubled := doubled * 2
                }
                prop {
                    AG (counted >= 0 && doubled > 0)
                }
            """,
        )

        assertThat(unsupported).isEmpty()
    }

    @Test
    fun `an unbounded integer without arithmetic that may leave 32 bits is not reported`() {
        val unsupported = unsupported(
            """
                var counter : int := 0
                var combined : int := 0
                init {
                }
                tran {
                    if (counter < 100) {
                        counter := counter + 1
                    }
                    combined := combined / 2 + counter
                }
                prop {
                    AG combined >= 0
                }
            """,
        )

        assertThat(unsupported).isEmpty()
    }

    @Test
    fun `arithmetic is reported only when it may leave 32 bits under the declared ranges`() {
        val unsupported = unsupported(
            """
                var counter : int := 1
                var doubled : int := 2
                var scaled : int := 100000
                init {
                }
                tran {
                    if (counter < 32767) {
                        counter := counter + 1
                    }
                    doubled := counter * 2
                    scaled := counter * 100000
                }
                prop {
                    AG (doubled > 0 && scaled > 0)
                }
            """,
        )

        assertThat(unsupported).containsExactly("arithmetic that may leave the 32-bit range")
    }
}
