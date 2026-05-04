/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import hu.bme.mit.semantifyr.backend.scopes.withVerificationScope
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalLocationKind
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalBackendModule
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.InlinedOxstsParseHelper
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UppaalOperationTransformerTest {
    private val injector = OxstsStandaloneSetup()
        .createInjectorAndDoEMFRegistration()
        .createChildInjector(UppaalBackendModule())
    private val parseHelper: InlinedOxstsParseHelper = injector.getInstance(InlinedOxstsParseHelper::class.java)

    @Test
    suspend fun `nested choices produce polynomial not exponential edges`() {
        withVerificationScope {
            val inlined = parseHelper.parse(
                """
                    inlined oxsts of semantifyr::Anything
                    var a : int := 0
                    var b : int := 0
                    var c : int := 0
                    init { }
                    tran {
                        choice { a := 1 } or { a := 2 } or { a := 3 }
                        choice { b := 1 } or { b := 2 } or { b := 3 }
                        choice { c := 1 } or { c := 2 } or { c := 3 }
                    }
                    prop { AG true }
                """.trimIndent(),
            )
            val template = buildTemplate(inlined)

            assertThat(template.edges.size).isLessThan(27)
            assertThat(template.edges.size).isGreaterThanOrEqualTo(9)

            assertThat(template.locations.map { it.name }).contains("Start", "Running")
            assertThat(template.locations.filter { it.kind == UppaalLocationKind.Committed })
                .hasSizeGreaterThanOrEqualTo(1 + 2)
        }
    }

    @Test
    suspend fun `sequence chains through committed intermediates`() {
        withVerificationScope {
            val inlined = parseHelper.parse(
                """
                    inlined oxsts of semantifyr::Anything
                    var x : int := 0
                    init { }
                    tran {
                        x := 1
                        x := 2
                        x := 3
                    }
                    prop { AG true }
                """.trimIndent(),
            )
            val template = buildTemplate(inlined)

            val committedCount = template.locations.count { it.kind == UppaalLocationKind.Committed }
            assertThat(committedCount).isEqualTo(1 + 2)

            val assignmentEdges = template.edges.filter { it.assignment != null }
            assertThat(assignmentEdges).hasSize(3)
            assertThat(assignmentEdges.map { it.assignment }).containsExactly("x = 1", "x = 2", "x = 3")
        }
    }

    @Test
    suspend fun `choice branches reconverge at the shared target`() {
        withVerificationScope {
            val inlined = parseHelper.parse(
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
            val template = buildTemplate(inlined)

            val branchEdges = template.edges.filter { it.sourceId == "running" && it.targetId == "running" }
            assertThat(branchEdges).hasSize(2)
            assertThat(branchEdges.map { it.assignment }).containsExactlyInAnyOrder("x = 1", "x = 2")
        }
    }

    @Test
    suspend fun `deeply nested choice sequence stays polynomial in edge count`() {
        withVerificationScope {
            val depth = 10
            val body = (1..depth).joinToString(separator = "\n    ") { i ->
                "choice { v$i := 1 } or { v$i := 2 } or { v$i := 3 }"
            }
            val declarations = (1..depth).joinToString(separator = "\n") { i ->
                "var v$i : int := 0"
            }
            val inlined = parseHelper.parse(
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
            val template = buildTemplate(inlined)

            val assignmentEdges = template.edges.count { it.assignment != null }
            assertThat(assignmentEdges).isEqualTo(depth * 3)
            assertThat(template.edges.size).isLessThan(100)
        }
    }

    @Test
    suspend fun `if operation emits guarded edges to intermediate then and else locations`() {
        withVerificationScope {
            val inlined = parseHelper.parse(
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
            val template = buildTemplate(inlined)

            val guardedFromRunning = template.edges.filter { it.sourceId == "running" && it.guard != null }
            assertThat(guardedFromRunning.map { it.guard }).containsExactlyInAnyOrder("b", "!(b)")
        }
    }

    private fun buildTemplate(inlined: InlinedOxsts) = injector
        .getInstance(UppaalModelTransformer::class.java)
        .buildNta(inlined)
        .templates
        .single()
}
