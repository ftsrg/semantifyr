/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import hu.bme.mit.semantifyr.backend.scopes.withVerificationScope
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvBackendModule
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.InlinedOxstsParseHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NuxmvOperationTransformerTest {
    private val injector = OxstsStandaloneSetup()
        .createInjectorAndDoEMFRegistration()
        .createChildInjector(NuxmvBackendModule)
    private val parseHelper: InlinedOxstsParseHelper = injector.getInstance(InlinedOxstsParseHelper::class.java)

    @Test
    suspend fun `single choice allocates one ivar and dispatches via case on the IVAR`() {
        withVerificationScope {
            val smv = generate(
                """
                    inlined oxsts of semantifyr::Anything
                    var x : int := 0
                    init { }
                    tran {
                        choice { x := 1 } or { x := 2 }
                    }
                    prop { AG true }
                """.trimIndent(),
            )

            assertThat(smv).contains("IVAR")
            assertThat(smv).containsPattern("""nondet_\d+ : 0\.\.1""")
            assertThat(smv).containsPattern("""case nondet_\d+ = 0 : \(x__tran_b0_1 = 1\); nondet_\d+ = 1 : \(x__tran_b0_2 = 2\); esac""")
            assertThat(smv).containsPattern("""x__tran_b0_3 = case nondet_\d+ = 0 : x__tran_b0_1; nondet_\d+ = 1 : x__tran_b0_2; esac""")
            assertThat(smv).contains("next(x) = x__tran_b0_3")
        }
    }

    @Test
    suspend fun `nested choice sequence stays polynomial in IVAR count`() {
        withVerificationScope {
            val depth = 10
            val body = (1..depth).joinToString(separator = "\n                        ") { i ->
                "choice { v$i := 1 } or { v$i := 2 } or { v$i := 3 }"
            }
            val declarations = (1..depth).joinToString(separator = "\n                    ") { i ->
                "var v$i : int := 0"
            }
            val smv = generate(
                """
                    inlined oxsts of semantifyr::Anything
                    $declarations
                    init { }
                    tran {
                        $body
                    }
                    prop { AG true }
                """.trimIndent(),
            )
            val ivarCount = Regex("""nondet_\d+ : 0\.\.2;""").findAll(smv).count()
            assertThat(ivarCount).isEqualTo(depth)
            for (i in 1..depth) {
                assertThat(smv).containsPattern("""next\(v$i\) = v${i}__tran_b0_\d+""")
            }
        }
    }

    @Test
    suspend fun `assumption inside a choice branch becomes a case-wrapped guard`() {
        withVerificationScope {
            val smv = generate(
                """
                    inlined oxsts of semantifyr::Anything
                    var x : int := 0
                    var y : int := 0
                    init { }
                    tran {
                        choice {
                            assume x > 0
                            y := 1
                        } or {
                            assume x < 0
                            y := 2
                        }
                    }
                    prop { AG true }
                """.trimIndent(),
            )
            assertThat(smv).containsPattern("""case nondet_\d+ = 0 : \(.*x > 0.*\); nondet_\d+ = 1 : \(.*x < 0.*\); esac""")
        }
    }

    @Test
    suspend fun `if operation uses guard-keyed case and introduces no ivar`() {
        withVerificationScope {
            val smv = generate(
                """
                    inlined oxsts of semantifyr::Anything
                    var b : bool := false
                    var x : int := 0
                    init { }
                    tran {
                        if (b) { x := 1 } else { x := 2 }
                    }
                    prop { AG true }
                """.trimIndent(),
            )
            assertThat(smv).doesNotContain("IVAR")
            assertThat(smv).containsPattern("""x__tran_b0_3 = case b : x__tran_b0_1; TRUE : x__tran_b0_2; esac""")
        }
    }

    @Test
    suspend fun `variable untouched in a choice branch retains its prior value`() {
        withVerificationScope {
            val smv = generate(
                """
                    inlined oxsts of semantifyr::Anything
                    var x : int := 0
                    var y : int := 0
                    init { }
                    tran {
                        choice { x := 1 } or { y := 1 }
                    }
                    prop { AG true }
                """.trimIndent(),
            )
            assertThat(smv).containsPattern("""x__tran_b0_2 = case nondet_\d+ = 0 : x__tran_b0_1; nondet_\d+ = 1 : x; esac""")
            assertThat(smv).containsPattern("""y__tran_b0_2 = case nondet_\d+ = 0 : y; nondet_\d+ = 1 : y__tran_b0_1; esac""")
        }
    }

    private fun generate(source: String): String {
        val generator = injector.getInstance(NuxmvModelGenerator::class.java)
        return generator.generate(parseHelper.parse(source)).smv
    }
}
