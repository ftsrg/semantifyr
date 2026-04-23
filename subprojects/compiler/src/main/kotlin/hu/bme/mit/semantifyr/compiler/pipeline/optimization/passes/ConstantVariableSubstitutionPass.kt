/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.context.EvaluableCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.tryEvaluateTypedOrNull
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.AnalysisManager
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationCategory
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Pass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.PassResult
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ConstantValueAnalysis
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import org.eclipse.xtext.EcoreUtil2

class ConstantVariableSubstitutionPass @Inject constructor(
    private val config: OptimizationConfig,
    private val metaStaticExpressionEvaluatorProvider: MetaStaticExpressionEvaluatorProvider,
    private val artifactManager: CompilationArtifactManager,
) : Pass<EvaluableCompilationContext> {

    override fun run(input: EvaluableCompilationContext, analysisManager: AnalysisManager): PassResult {
        if (!config.isEnabled(OptimizationCategory.ConstantFolding)) {
            return PassResult.Unchanged
        }

        val constants = analysisManager.get(ConstantValueAnalysis::class.java, input)
        if (constants.constants.isEmpty()) {
            return PassResult.Unchanged
        }

        val evaluator = metaStaticExpressionEvaluatorProvider.getEvaluator(input.rootInstance)

        val substitutions = input.inlinedOxsts.eAllOfType<Expression>().filterNot {
            OxstsUtils.isWriteExpression(it)
        }.mapNotNull {
            val variable = evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, it)
                ?: return@mapNotNull null
            val constant = constants.valueOf(variable) ?: return@mapNotNull null
            it to constant
        }.toList()

        if (substitutions.isEmpty()) {
            return PassResult.Unchanged
        }

        for ((readExpression, constantExpression) in substitutions) {
            EcoreUtil2.replace(readExpression, constantExpression.copy())
            artifactManager.commitStep(CompilationPass.ConstantVariableSubstitution)
        }
        return PassResult.Changed()
    }

}
