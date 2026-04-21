/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import com.google.inject.assistedinject.Assisted
import com.google.inject.assistedinject.AssistedInject
import hu.bme.mit.semantifyr.oxsts.lang.utils.ExpressionVisitor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticBinaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInfinity
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralNothing
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralReal
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralString
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NegationOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RangeExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SelfReference
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import org.eclipse.xtext.EcoreUtil2

/**
 * Orchestrator that walks expressions and delegates per-node rewrites to
 * [ExpressionCallExpander]. Pure BFS walking + dispatch; no expansion logic
 * lives here.
 */
class ExpressionCallInliner @AssistedInject constructor(
    @param:Assisted val instance: Instance,
    private val compilationArtifactManager: CompilationArtifactManager,
    private val expressionCallExpander: ExpressionCallExpander,
) : ExpressionVisitor<Unit>() {

    private val processorQueue: ArrayDeque<Expression> = ArrayDeque<Expression>()

    fun process(expression: Expression) {
        visit(expression)
        processAll()
    }

    private fun processAll() {
        while (processorQueue.any()) {
            val next = processorQueue.removeFirst()
            visit(next)
        }
    }

    override fun visit(expression: RangeExpression) {
        processorQueue.addFirst(expression.left)
        processorQueue.addFirst(expression.right)
    }

    override fun visit(expression: ComparisonOperator) {
        processorQueue.addFirst(expression.left)
        processorQueue.addFirst(expression.right)
    }

    override fun visit(expression: ArithmeticBinaryOperator) {
        processorQueue.addFirst(expression.left)
        processorQueue.addFirst(expression.right)
    }

    override fun visit(expression: BooleanOperator) {
        processorQueue.addFirst(expression.left)
        processorQueue.addFirst(expression.right)
    }

    override fun visit(expression: ArithmeticUnaryOperator) {
        processorQueue.addFirst(expression.body)
    }

    override fun visit(expression: NegationOperator) {
        processorQueue.addFirst(expression.body)
    }

    override fun visit(expression: AG) {
        processorQueue.addFirst(expression.body)
    }

    override fun visit(expression: EF) {
        processorQueue.addFirst(expression.body)
    }

    override fun visit(expression: ArrayLiteral) {
        for (index in expression.values.indices.reversed()) {
            processorQueue.addFirst(expression.values[index])
        }
    }

    override fun visit(expression: LiteralInfinity) {
        // NO-OP
    }

    override fun visit(expression: LiteralReal) {
        // NO-OP
    }

    override fun visit(expression: LiteralInteger) {
        // NO-OP
    }

    override fun visit(expression: LiteralString) {
        // NO-OP
    }

    override fun visit(expression: LiteralBoolean) {
        // NO-OP
    }

    override fun visit(expression: LiteralNothing) {
        // NO-OP
    }

    override fun visit(expression: ElementReference) {
        rewriteFeatureReference(expression)
    }

    override fun visit(expression: SelfReference) {
        // NO-OP
    }

    override fun visit(expression: NavigationSuffixExpression) {
        rewriteFeatureReference(expression)
    }

    override fun visit(expression: CallSuffixExpression) {
        val inlined = expressionCallExpander.expandCall(expression, instance)
        EcoreUtil2.replace(expression, inlined)
        processorQueue.addFirst(inlined)

        compilationArtifactManager.commitStep(CompilationPass.ExpressionCallInlining)
    }

    override fun visit(expression: IndexingSuffixExpression) {
        processorQueue.addFirst(expression.index)
    }

    private fun rewriteFeatureReference(expression: Expression) {
        val rewritten = expressionCallExpander.expandFeatureReferenceOrNull(expression, instance) ?: return
        EcoreUtil2.replace(expression, rewritten)
        compilationArtifactManager.commitStep(CompilationPass.ExpressionCallInlining)
    }

    interface Factory {
        fun create(instance: Instance): ExpressionCallInliner
    }

}
