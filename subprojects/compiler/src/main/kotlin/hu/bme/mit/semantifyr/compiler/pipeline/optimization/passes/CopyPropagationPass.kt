/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.passes

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.context.EvaluableCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.tryEvaluateTypedOrNull
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.AnalysisManager
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationCategory
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.optimizers.Pass
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.optimizers.PassResult
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses.ReachingDefinitionsAnalysis
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import org.eclipse.xtext.EcoreUtil2
import kotlin.collections.iterator

/**
 * Copy propagation using [ReachingDefinitionsAnalysis].
 *
 * If a variable read has exactly one reaching definition, and that definition
 * is an [AssignmentOperation] whose RHS is a simple "copyable" expression
 * (literal or variable reference), the read can be replaced with a copy of
 * the RHS. This removes a layer of indirection and often exposes further
 * constant folding or simplification opportunities.
 *
 * Conservative about what's copyable: only literals and direct element
 * references. Complex expressions are not propagated because they might
 * reference variables that change between the assignment and the read.
 */
class CopyPropagationPass @Inject constructor(
    private val config: OptimizationConfig,
    private val metaStaticExpressionEvaluatorProvider: MetaStaticExpressionEvaluatorProvider,
    private val artifactManager: CompilationArtifactManager,
) : Pass<EvaluableCompilationContext> {

    override fun run(input: EvaluableCompilationContext, analyses: AnalysisManager): PassResult {
        if (!config.isEnabled(OptimizationCategory.ConstantFolding)) {
            return PassResult.Unchanged
        }

        val rd = analyses.get(ReachingDefinitionsAnalysis::class.java, input)
        val evaluator = metaStaticExpressionEvaluatorProvider.getEvaluator(input.rootInstance)

        val substitutions = buildList {
            for ((read, defs) in rd.defsOf) {
                val definition = defs.singleOrNull() as? AssignmentOperation ?: continue
                val candidate = definition.expression
                if (!isSimplyCopyable(candidate)) continue

                // Avoid infinite self-copy: don't replace a read of x with x.
                val readVariable = evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, read)
                val rhsVariable = evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, candidate)
                if (readVariable != null && rhsVariable == readVariable) continue

                add(read to candidate)
            }
        }

        if (substitutions.isEmpty()) return PassResult.Unchanged

        for ((read, source) in substitutions) {
            EcoreUtil2.replace(read, source.copy())
            artifactManager.commitStep(CompilationPass.ConstantFolding)
        }
        return PassResult.changed()
    }

    /** A copyable expression is one we can freely duplicate at a read site. */
    private fun isSimplyCopyable(expression: Expression): Boolean {
        if (OxstsUtils.isWriteExpression(expression)) return false
        return expression is LiteralExpression || expression is ElementReference
    }

}
