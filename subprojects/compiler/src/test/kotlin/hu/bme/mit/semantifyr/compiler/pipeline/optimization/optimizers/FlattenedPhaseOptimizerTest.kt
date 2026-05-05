/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.optimizers

import com.google.inject.Injector
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationModule
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationRequest
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.PassTestBase
import hu.bme.mit.semantifyr.compiler.pipeline.utils.serializeFormatted
import hu.bme.mit.semantifyr.compiler.scopes.withCompilationScopeBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

class FlattenedPhaseOptimizerTest : PassTestBase() {

    @Test
    fun `init choice with two distinct constants must not collapse the variable to its default`() = assertOptimizerTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var current : int := -1
            init {
                choice {
                    current := 0
                } or {
                    current := 1
                }
            }
            tran { }
            prop { EF (current == 0 || current == 1) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var current : int := -1
            init {
                choice {
                    current := 0
                } or {
                    current := 1
                }
            }
            tran { }
            prop { EF (current == 0 || current == 1) }
        """,
    )

    @Test
    fun `init choice plus tran reads must keep both init writes alive`() = assertOptimizerTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var current : int := -1
            var done_first : bool := false
            var done_second : bool := false
            init {
                choice {
                    current := 0
                } or {
                    current := 1
                }
            }
            tran {
                choice {
                    assume (current == 0)
                    done_first := true
                } or {
                    assume (current == 1)
                    done_second := true
                }
            }
            prop { EF (done_first == true || done_second == true) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var current : int := -1
            var done_first : bool := false
            var done_second : bool := false
            init {
                choice {
                    current := 0
                } or {
                    current := 1
                }
            }
            tran {
                choice {
                    assume (current == 0)
                    done_first := true
                } or {
                    assume (current == 1)
                    done_second := true
                }
            }
            prop { EF (done_first == true || done_second == true) }
        """,
    )

    @Test
    fun `single tran write that is reached only via the loop is not folded into the property`() = assertOptimizerTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var done_first : bool := false
            init { }
            tran {
                done_first := true
            }
            prop { EF (done_first == true) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var done_first : bool := false
            init { }
            tran {
                done_first := true
            }
            prop { EF (done_first == true) }
        """,
    )

    @Test
    fun `assumes guarding tran keep their read variables in the cone of influence`() = assertOptimizerTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var step : int := -1
            var aux : int := 0
            init {
                step := 1
            }
            tran {
                assume (step == 1)
                step := 2
            }
            prop { AG (step != 2 || aux < 3) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var step : int := -1
            init {
                step := 1
            }
            tran {
                assume (step == 1)
                step := 2
            }
            prop { AG (true) }
        """,
    )

    @Test
    fun `step counter style witness model - init writes must survive`() = assertOptimizerTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var step : int := -1
            init {
                assume (step == -1)
                step := 0
                assume (step == 0)
                step := 1
            }
            tran {
                assume (step == 1)
                step := 2
            }
            prop { AG (step != 2) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var step : int := -1
            init {
                assume (step == -1)
                step := 0
                step := 1
            }
            tran {
                assume (step == 1)
                step := 2
            }
            prop { AG (step != 2) }
        """,
    )

    @Test
    fun `nested choice in init - all branches stay live when read in tran`() = assertOptimizerTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var current : int := -1
            init {
                choice {
                    choice {
                        current := 0
                    } or {
                        current := 1
                    }
                } or {
                    current := 2
                }
            }
            tran {
                assume (current == 0 || current == 1 || current == 2)
            }
            prop { AG (current != 99) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var current : int := -1
            init {
                choice {
                    current := 2
                } or {
                    current := 0
                } or {
                    current := 1
                }
            }
            tran {
                assume (current == 0 || current == 1 || current == 2)
            }
            prop { AG (current != 99) }
        """,
    )

    @Test
    fun `witness shape with step counter and dispatch must be preserved`() = assertOptimizerTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            var step : int := -1
            var current : int := -1
            var first_done : bool := false
            var second_done : bool := false
            init {
                assume (step == -1)
                assume (current == -1)
                assume (first_done == false)
                assume (second_done == false)
                step := 0
                assume (step == 0)
                choice {
                    current := 0
                } or {
                    current := 1
                }
                assume (current == 1)
                assume (first_done == false)
                assume (second_done == false)
                step := 1
            }
            tran {
                assume (step == 1)
                choice {
                    assume (current == 0)
                    first_done := true
                } or {
                    assume (current == 1)
                    second_done := true
                }
                assume (current == 1)
                assume (first_done == false)
                assume (second_done == true)
                step := 2
            }
            prop { EF (step == 2 && (first_done == true || second_done == true)) }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            var step : int := -1
            var current : int := -1
            var first_done : bool := false
            var second_done : bool := false
            init {
                assume (step == -1)
                assume (current == -1)
                assume (first_done == false)
                assume (second_done == false)
                step := 0
                choice {
                    current := 0
                } or {
                    current := 1
                }
                assume (current == 1)
                assume (first_done == false)
                assume (second_done == false)
                step := 1
            }
            tran {
                assume (step == 1)
                choice {
                    assume (current == 0)
                    first_done := true
                } or {
                    assume (current == 1)
                    second_done := true
                }
                assume (current == 1)
                assume (first_done == false)
                assume (second_done == true)
                step := 2
            }
            prop { EF (step == 2 && (first_done == true || second_done == true)) }
        """,
    )

    @Test
    fun `NonOptimizable variable and its writes survive the full optimizer pipeline`() = assertOptimizerTransforms(
        source = """
            inlined oxsts of semantifyr::Anything
            @NonOptimizable
            var tracer : bool := false
            init { }
            tran { tracer := true }
            prop { AG true }
        """,
        expectedSource = """
            inlined oxsts of semantifyr::Anything
            @NonOptimizable
            var tracer : bool := false
            init { }
            tran { tracer := true }
            prop { AG true }
        """,
    )

    private fun assertOptimizerTransforms(
        source: String,
        expectedSource: String,
    ) {
        val actual = compile(source)
        val expected = compile(expectedSource)

        val child: Injector = injector.createChildInjector(
            CompilationModule(
                ArtifactConfig.NONE,
                OptimizationConfig.ALL,
            ),
        )

        val request = CompilationRequest(
            inlinedOxsts = actual.inlinedOxsts,
            outputDirectory = Files.createTempDirectory("optimizer-test-"),
        )
        val optimizerName = withCompilationScopeBlocking(request) {
            val optimizer = child.getInstance(FlattenedPhaseOptimizer::class.java)
            optimizer.optimize(actual.context)
            optimizer::class.simpleName
        }

        val actualText = serializer.serializeFormatted(actual.inlinedOxsts)
        val expectedText = serializer.serializeFormatted(expected.inlinedOxsts)
        assertThat(actualText)
            .describedAs("$optimizerName should not change a model whose passes individually leave it alone")
            .isEqualTo(expectedText)
    }
}
