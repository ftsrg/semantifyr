/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.optimizers

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.expression.ConstantExpressionEvaluationTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Optimizer
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.PatternOptimizer
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.AssumeFalsePropagationPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.ConstantGuardIfPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.FlattenNestedChoicePattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.FlattenNestedSequencePattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.FlattenSingleBranchChoicePattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.FlatteningPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.PropagateBothBranchesConstantFalsePattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.PropagateConstantFalseInSequencePattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.PropagateSingleBranchConstantFalsePattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.RedundancyPatterns
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.RemoveConstantFalseChoiceBranchPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.RemoveConstantTrueAssumptionPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.RemoveEmptyForPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.RemoveEmptyIfBodyPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.RemoveEmptyIfElsePattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.RemoveRedundantEmptyChoiceBranchPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.BubbleNotAGPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.BubbleNotEFPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.ConstantFalseAndPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.ConstantTrueOrPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.DoubleNegationPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.RedundantAndPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.RedundantOrPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.ConstantFoldingPattern

class NestedOperationOptimizer @Inject constructor(
    evaluator: ConstantExpressionEvaluatorProvider,
    transformer: ConstantExpressionEvaluationTransformer,
    artifactManager: CompilationArtifactManager,
) : Optimizer<Operation>() {

    private val patternOptimizer = PatternOptimizer(
        patterns = listOf(
            AssumeFalsePropagationPattern(),
            FlatteningPattern(),
            RedundancyPatterns(),
            // Expression simplification
            DoubleNegationPattern(),
            ConstantTrueOrPattern(),
            ConstantFalseAndPattern(),
            RedundantOrPattern(),
            RedundantAndPattern(),
            BubbleNotEFPattern(),
            BubbleNotAGPattern(),
            ConstantFoldingPattern(evaluator, transformer),
        ),
        pass = CompilationPass.OperationCallInlining,
        artifactManager = artifactManager,
    )

    override fun optimize(input: Operation): Boolean {
        return patternOptimizer.optimize(input)
    }

}
