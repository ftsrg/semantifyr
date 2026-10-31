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
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.AssumeFalsePropagationPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.ConstantVariableSubstitutionPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.CopyPropagationPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.DeadCodeRemovalPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.DeadStoreEliminationPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.ExpressionSimplificationPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.OperationFlatteningPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.RedundantOperationRemovalPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes.VariableLivenessPass
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.time.Duration

@InjectWithOxsts
class CompilationMetricsTest {

    @Inject
    private lateinit var injector: Injector

    @Inject
    private lateinit var loader: SemantifyrLoader

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `model sizes count the smoke case at every level`() {
        val metrics = compileSmokeCase(OptimizationConfig.ALL)

        assertThat(metrics.inputSize).isEqualTo(ModelSize(variables = 1, operations = 4, expressions = 13))
        assertThat(metrics.inlinedSize.variables).isEqualTo(1)
        assertThat(metrics.flattenedSize.variables).isEqualTo(1)
        assertThat(metrics.inlinedSize.operations).isPositive
        assertThat(metrics.flattenedSize.expressions).isPositive
    }

    @Test
    fun `every phase is timed and unfolding is their sum`() {
        val metrics = compileSmokeCase(OptimizationConfig.ALL)

        assertThat(listOf(metrics.createDuration, metrics.instantiationDuration, metrics.inliningDuration, metrics.flatteningDuration))
            .allMatch { it.isPositive() }
        assertThat(metrics.unfoldingDuration)
            .isEqualTo(metrics.createDuration + metrics.instantiationDuration + metrics.inliningDuration + metrics.flatteningDuration)
        assertThat(metrics.inliningOptimization.duration).isLessThanOrEqualTo(metrics.inliningDuration)
        assertThat(metrics.flatteningOptimization.duration).isLessThanOrEqualTo(metrics.flatteningDuration)
    }

    @Test
    fun `optimizer stats name the passes of each phase`() {
        val metrics = compileSmokeCase(OptimizationConfig.ALL)

        assertThat(metrics.inliningOptimization.passes.map { it.name }).containsExactly(
            CompilationPass.OperationCallInlining.name,
            ExpressionSimplificationPass::class.simpleName,
            OperationFlatteningPass::class.simpleName,
            RedundantOperationRemovalPass::class.simpleName,
            AssumeFalsePropagationPass::class.simpleName,
            CompilationPass.PropertyExpressionNormalization.name,
        )
        assertThat(metrics.flatteningOptimization.passes.map { it.name }).containsExactly(
            ExpressionSimplificationPass::class.simpleName,
            OperationFlatteningPass::class.simpleName,
            RedundantOperationRemovalPass::class.simpleName,
            AssumeFalsePropagationPass::class.simpleName,
            ConstantVariableSubstitutionPass::class.simpleName,
            CopyPropagationPass::class.simpleName,
            DeadStoreEliminationPass::class.simpleName,
            DeadCodeRemovalPass::class.simpleName,
            VariableLivenessPass::class.simpleName,
        )
        assertThat(metrics.flatteningOptimization.passes.map { it.runs }.distinct()).hasSize(1)
        assertThat(metrics.flatteningOptimization.passes).allMatch { it.firings <= it.runs }
        assertThat(metrics.flatteningOptimization.analyses.map { it.name }).isSubsetOf(
            LivenessAnalysis::class.simpleName,
            ConeOfInfluenceAnalysis::class.simpleName,
            ConstantValueAnalysis::class.simpleName,
            ReachingDefinitionsAnalysis::class.simpleName,
        )
    }

    @Test
    fun `counters and firings are deterministic`() {
        val first = compileSmokeCase(OptimizationConfig.ALL).withoutDurations()
        val second = compileSmokeCase(OptimizationConfig.ALL).withoutDurations()

        assertThat(second).isEqualTo(first)
    }

    private fun compileSmokeCase(optimization: OptimizationConfig): CompilationMetrics {
        val context = loader.loadStandaloneModel(Path("../../oxsts-test-models/smoke/smoke.oxsts"))
        val classDeclaration = context.modelResources.asSequence().flatMap {
            it.contents
        }.filterIsInstance<OxstsModelPackage>().flatMap {
            it.declarations
        }.filterIsInstance<ClassDeclaration>().single()
        val compiler = SemantifyrCompiler(injector, ArtifactConfig.NONE, optimization)
        return compiler.compile(classDeclaration, tempDir).compilationMetrics
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
}
