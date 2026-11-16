/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.flattening

import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

class FlatteningStepsTest : FlatteningTestBase() {

    private val everyRewrite = """
        package flattening::steps
        record Point {
            var x: int := 0
        }
        class Worker {
            var done: bool := false
        }
        @VerificationCase
        class Host {
            contains first: Worker
            contains second: Worker
            var position: Point := Point { x = 1 }
            var values: int[2] := [1, 2]
            var current: Worker[0..1] := nothing
            redefine tran {
                var picked: Worker := first
                havoc(picked)
                havoc(current)
                position.x := values[0]
                if (picked == current) {
                    first.done := true
                }
            }
            prop {
                return AG !first.done || position.x == 1
            }
        }
    """

    private val variablesOnly = """
        package flattening::steps
        class Worker {
            var done: bool := false
        }
        @VerificationCase
        class Host {
            contains worker: Worker
            redefine tran {
                worker.done := true
            }
            prop {
                return EF worker.done
            }
        }
    """

    private val flatteningPasses = listOf(
        CompilationPass.VariablePullDown,
        CompilationPass.VariableReferenceRewriting,
        CompilationPass.LocalInstanceDomainRegistration,
        CompilationPass.RecordFlattening,
        CompilationPass.ArrayFlattening,
        CompilationPass.FeatureTypedVariableRewriting,
        CompilationPass.StaticExpressionRewriting,
    )

    private fun steps(): List<Path> {
        return temporaryDirectory.resolve("pipeline/steps").listDirectoryEntries().sortedBy {
            it.name
        }
    }

    private fun Path.pass(): String {
        return name.substringAfter('_').substringBefore('.')
    }

    private fun flatteningSteps(): List<Path> {
        val names = flatteningPasses.map {
            it.name.lowercase()
        }
        return steps().filter {
            it.pass() in names
        }
    }

    @Test
    fun `flattening writes a frame after each of its rewrites in their order, each one changing the model`() {
        flatten("Host", everyRewrite, artifacts = ArtifactConfig.DEBUG)

        val frames = flatteningSteps()
        assertThat(frames.map { it.pass() }).containsExactlyElementsOf(
            flatteningPasses.map {
                it.name.lowercase()
            },
        )
        val texts = listOf(temporaryDirectory.resolve("pipeline/inlined.semantifyr").readText()) + frames.map {
            it.readText()
        }
        assertThat(texts.zipWithNext()).noneMatch {
            it.first == it.second
        }
        assertThat(frames.last().readText()).isEqualTo(temporaryDirectory.resolve("pipeline/flattened.semantifyr").readText())
    }

    @Test
    fun `a rewrite that changes nothing in the model writes no frame`() {
        flatten("Host", variablesOnly, artifacts = ArtifactConfig.DEBUG)

        assertThat(flatteningSteps().map { it.pass() }).containsExactly(
            CompilationPass.VariablePullDown.name.lowercase(),
            CompilationPass.VariableReferenceRewriting.name.lowercase(),
            CompilationPass.StaticExpressionRewriting.name.lowercase(),
        )
    }

    @Test
    fun `a compilation that keeps no frames writes none`() {
        flatten("Host", everyRewrite, artifacts = ArtifactConfig.ALL)

        assertThat(temporaryDirectory.resolve("pipeline/steps").exists()).isFalse
    }

}
