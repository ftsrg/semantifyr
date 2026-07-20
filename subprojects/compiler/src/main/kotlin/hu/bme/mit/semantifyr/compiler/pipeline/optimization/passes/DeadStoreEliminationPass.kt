/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.context.EvaluableCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaCompileTimeExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.tryEvaluateTypedOrNull
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.AnalysisManager
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Pass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.PassResult
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ReachingDefinitionsAnalysis
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.pipeline.utils.writeReference
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.xtext.EcoreUtil2

class DeadStoreEliminationPass @Inject constructor(
    private val config: OptimizationConfig,
    private val artifactManager: CompilationArtifactManager,
    private val builtinAnnotationHandler: BuiltinAnnotationHandler,
    private val metaCompileTimeExpressionEvaluatorProvider: MetaCompileTimeExpressionEvaluatorProvider,
) : Pass<EvaluableCompilationContext> {

    override fun run(input: EvaluableCompilationContext, analysisManager: AnalysisManager): PassResult {
        if (!config.isEnabled(OptimizationPass.DeadStoreElimination)) {
            return PassResult.Unchanged
        }

        val reachingDefinitionsInfo = analysisManager.get(ReachingDefinitionsAnalysis::class.java, input)

        val liveWrites = reachingDefinitionsInfo.definitionsOf.values.flatten().toSet()
        val exitLiveWrites = reachingDefinitionsInfo.exitReaching

        val evaluator = metaCompileTimeExpressionEvaluatorProvider.getEvaluator(input.rootInstance)

        val deadWrites = input.inlinedOxsts.eAllOfType<Operation>().filter {
            it is AssignmentOperation || it is HavocOperation
        }.filterNot {
            it in liveWrites
        }.filterNot {
            it in exitLiveWrites
        }.filterNot {
            val variable = evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, it.writeReference())
            variable != null && builtinAnnotationHandler.isNonOptimizable(variable)
        }.toList()

        if (deadWrites.isEmpty()) {
            return PassResult.Unchanged
        }

        for (operation in deadWrites) {
            EcoreUtil2.remove(operation)
            artifactManager.commitStep(CompilationPass.DeadCodeRemoval)
        }
        return PassResult.Changed
    }

}
