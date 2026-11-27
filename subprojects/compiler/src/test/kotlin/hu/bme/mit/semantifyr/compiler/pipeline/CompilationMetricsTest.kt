/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.context.CompilationMetrics
import hu.bme.mit.semantifyr.compiler.pipeline.context.ModelSize
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationStats
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ConeOfInfluenceAnalysis
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ConstantValueAnalysis
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.LivenessAnalysis
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ReachingDefinitionsAnalysis
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration

@InjectWithOxsts
class CompilationMetricsTest {

    @Inject
    private lateinit var injector: Injector

    @Inject
    private lateinit var parseHelper: OxstsPackageParseHelper

    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `model sizes count the model at every level`() {
        val metrics = compileModel()

        assertThat(metrics.inputSize).isEqualTo(ModelSize(variables = 1, operations = 7, expressions = 24))
        assertThat(metrics.inlinedSize.variables).isEqualTo(2)
        assertThat(metrics.flattenedSize.variables).isEqualTo(2)
        assertThat(metrics.inlinedSize.operations).isPositive
        assertThat(metrics.flattenedSize.expressions).isPositive
    }

    @Test
    fun `every phase is timed and unfolding is their sum`() {
        val metrics = compileModel()

        assertThat(listOf(metrics.createDuration, metrics.instantiationDuration, metrics.inliningDuration, metrics.flatteningDuration))
            .allMatch { it.isPositive() }
        assertThat(metrics.unfoldingDuration)
            .isEqualTo(metrics.createDuration + metrics.instantiationDuration + metrics.inliningDuration + metrics.flatteningDuration)
        assertThat(metrics.inliningOptimization.duration).isLessThanOrEqualTo(metrics.inliningDuration)
        assertThat(metrics.flatteningOptimization.duration).isLessThanOrEqualTo(metrics.flatteningDuration)
    }

    @Test
    fun `the inliner's own steps frame its optimizer's passes and every flattened pass runs alike`() {
        val metrics = compileModel()

        val inliningPasses = metrics.inliningOptimization.passes.map {
            it.name
        }
        assertThat(inliningPasses.first()).isEqualTo(CompilationPass.OperationCallInlining.name)
        assertThat(inliningPasses.last()).isEqualTo(CompilationPass.PropertyExpressionNormalization.name)
        assertThat(inliningPasses).hasSizeGreaterThan(2)
        assertThat(metrics.flatteningOptimization.passes).isNotEmpty
        val runs = metrics.flatteningOptimization.passes.map {
            it.runs
        }
        assertThat(runs.distinct()).hasSize(1)
        assertThat(metrics.flatteningOptimization.passes).allMatch {
            it.firings <= it.runs
        }
        val analyses = metrics.flatteningOptimization.analyses.map {
            it.name
        }
        assertThat(analyses).isSubsetOf(
            LivenessAnalysis::class.simpleName,
            ConeOfInfluenceAnalysis::class.simpleName,
            ConstantValueAnalysis::class.simpleName,
            ReachingDefinitionsAnalysis::class.simpleName,
        )
    }

    @Test
    fun `counters and firings are deterministic`() {
        val first = compileModel().withoutDurations()
        val second = compileModel().withoutDurations()

        assertThat(second).isEqualTo(first)
    }

    private fun compileModel(): CompilationMetrics {
        val parsed = parseHelper.parse(MODEL)
        parsed.assertNoResourceErrors()
        val classDeclaration = parsed.classByName("Host").eObject()
        val compiler = SemantifyrCompiler(injector, ArtifactConfig.NONE, OptimizationConfig.ALL)
        return compiler.compile(classDeclaration, temporaryDirectory).compilationMetrics
    }

    private fun CompilationMetrics.withoutDurations(): CompilationMetrics {
        return copy(
            createDuration = Duration.ZERO,
            instantiationDuration = Duration.ZERO,
            inliningDuration = Duration.ZERO,
            flatteningDuration = Duration.ZERO,
            inliningOptimization = inliningOptimization.withoutDurations(),
            flatteningOptimization = flatteningOptimization.withoutDurations(),
        )
    }

    private fun OptimizationStats.withoutDurations(): OptimizationStats {
        return copy(
            duration = Duration.ZERO,
            passes = passes.map {
                it.copy(duration = Duration.ZERO)
            },
            analyses = analyses.map {
                it.copy(duration = Duration.ZERO)
            },
        )
    }

    companion object {
        // two instances of one class, so that the inlined and the flattened model count a variable per instance
        private val MODEL = """
            package compilation::metrics

            class Counter {
                var count: int := 0

                tran step() {
                    if (count < 3) {
                        count := count + 1
                    }
                }
            }

            @VerificationCase
            class Host {
                contains first: Counter
                contains second: Counter

                redefine tran {
                    inline first.step()
                    inline second.step()
                }

                redefine prop {
                    return EF (first.count == 3 && second.count == 3)
                }
            }
        """.trimIndent()
    }
}
