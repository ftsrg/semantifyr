/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendCapabilityChecker
import hu.bme.mit.semantifyr.backend.scopes.withVerificationScopeBlocking
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvBackend
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NuxmvCapabilityCheckerTest : NuxmvTransformationTestBase() {

    @Inject
    private lateinit var oxstsInjector: Injector

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

    private fun unsupportedByBdd(body: String): List<String> {
        val inlinedOxsts = parse("inlined oxsts of semantifyr::Anything\n${body.trimIndent()}", colors)
        return bddCapabilityChecker().check(inlinedOxsts).map {
            it.description
        }
    }

    @Test
    fun `the BDD configuration refuses an integer variable`() {
        val unsupported = unsupportedByBdd(
            """
                var counter : int := 0
                var flag : bool := false
                init {
                }
                tran {
                    counter := counter + 1
                }
                prop {
                    AG counter < 3
                }
            """,
        )

        assertThat(unsupported).containsExactly("integer variables")
    }

    @Test
    fun `the BDD configuration refuses a clock`() {
        val unsupported = unsupportedByBdd(
            """
                var elapsed : clock := 0
                var late : bool := false
                init {
                }
                tran {
                    assume(elapsed <= 5)
                    late := true
                }
                prop {
                    AG !late
                }
            """,
        )

        assertThat(unsupported).containsExactly("clocks")
    }

    @Test
    fun `the SMT configurations accept a clock`() {
        val unsupported = unsupported(
            """
                var elapsed : clock := 0
                var late : bool := false
                init {
                }
                tran {
                    assume(elapsed >= 6)
                    late := true
                    elapsed := 0
                }
                prop {
                    AG !late
                }
            """,
        )

        assertThat(unsupported).isEmpty()
    }

    @Test
    fun `the BDD configuration accepts boolean and enumeration variables`() {
        val unsupported = unsupportedByBdd(
            """
                var flag : bool := false
                var color : test::colors::Color := test::colors::Color::Red
                init {
                    havoc(flag)
                }
                tran {
                    if (flag) {
                        color := test::colors::Color::Green
                    }
                }
                prop {
                    AG color == test::colors::Color::Red
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

    @Test
    fun `the BDD configuration gets the BDD checker and the SMT configurations the plain one`() {
        assertThat(checkerOf(NuxmvConfig.BddInvar)).isInstanceOf(NuxmvBddCapabilityChecker::class.java)
        assertThat(checkerOf(NuxmvConfig.KInductionInvar)).isExactlyInstanceOf(NuxmvCapabilityChecker::class.java)
    }

    private fun checkerOf(config: NuxmvConfig): BackendCapabilityChecker {
        return withVerificationScopeBlocking {
            NuxmvBackend().capabilityChecker(oxstsInjector, config)
        }
    }
}
