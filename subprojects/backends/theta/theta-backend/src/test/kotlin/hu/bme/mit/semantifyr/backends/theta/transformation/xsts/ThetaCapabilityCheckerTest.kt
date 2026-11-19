/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.transformation.xsts

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendCapabilityChecker
import hu.bme.mit.semantifyr.backend.scopes.withVerificationScopeBlocking
import hu.bme.mit.semantifyr.backends.theta.ThetaBackend
import hu.bme.mit.semantifyr.backends.theta.ThetaConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ThetaCapabilityCheckerTest : ThetaTransformationTestBase() {

    @Inject
    private lateinit var oxstsInjector: Injector

    private val colors = """
        package test::colors
        enum Color {
            Red,
            Green,
        }
    """

    private val notes = """
        package test::notes
        annotation Note(text: string)
    """

    private fun unsupported(body: String, type: Class<out ThetaCapabilityChecker> = ThetaCapabilityChecker::class.java): List<String> {
        val inlinedOxsts = parse("inlined oxsts of semantifyr::Anything\n${body.trimIndent()}", colors, notes)
        return capabilityChecker(type).check(inlinedOxsts).map {
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
                var elapsed : clock := 0
                init {
                    havoc(flag)
                }
                tran {
                    choice {
                        assume(counter < 3)
                        assume(elapsed <= 2)
                        counter := counter + 1
                        elapsed := 0
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
            "real literals",
            "variables of type string",
            "string literals",
        )
    }

    @Test
    fun `a string argument of an annotation on a variable passes while a string variable does not`() {
        val unsupported = unsupported(
            """
                @test::notes::Note("a counter")
                var counter : int := 0
                var text : string := "text"
                init {
                }
                tran {
                    counter := counter + 1
                }
                prop {
                    AG counter >= 0
                }
            """,
        )

        assertThat(unsupported).containsExactlyInAnyOrder(
            "variables of type string",
            "string literals",
        )
    }

    private fun unsupportedWithClockBound(bound: Int): List<String> {
        return unsupported(
            """
                var elapsed : clock := 0
                var late : bool := false
                init {
                }
                tran {
                    choice {
                        assume(elapsed >= $bound)
                        late := true
                        elapsed := 0
                    } or {
                    }
                }
                prop {
                    AG !late
                }
            """,
        )
    }

    @Test
    fun `a clock bound one below the largest 32-bit integer is accepted`() {
        assertThat(unsupportedWithClockBound(2147483646)).isEmpty()
    }

    @Test
    fun `a clock bound at the largest 32-bit integer is reported, since its saturation is one above it`() {
        assertThat(unsupportedWithClockBound(2147483647)).containsExactly("clocks bounded at the largest 32-bit integer")
    }

    @Test
    fun `Horn solving refuses a clock test that is not closed, which the other configurations accept`() {
        val strictTest = """
            var elapsed : clock := 0
            var late : bool := false
            init {
            }
            tran {
                choice {
                    assume(elapsed > 2)
                    late := true
                    elapsed := 0
                } or {
                }
            }
            prop {
                AG !late
            }
        """

        assertThat(unsupported(strictTest, ThetaHornCapabilityChecker::class.java)).containsExactly("clock tests that are not closed")
        assertThat(unsupported(strictTest)).isEmpty()
    }

    @Test
    fun `the over-approximating configuration gets the Horn checker and the others the plain one`() {
        assertThat(checkerOf(ThetaConfig.Chc)).isInstanceOf(ThetaHornCapabilityChecker::class.java)
        assertThat(checkerOf(ThetaConfig.CegarExpl)).isExactlyInstanceOf(ThetaCapabilityChecker::class.java)
    }

    private fun checkerOf(config: ThetaConfig): BackendCapabilityChecker {
        return withVerificationScopeBlocking {
            ThetaBackend().capabilityChecker(oxstsInjector, config)
        }
    }
}
