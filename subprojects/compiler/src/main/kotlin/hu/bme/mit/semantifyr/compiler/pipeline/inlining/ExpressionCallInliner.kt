/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.inlining

import com.google.inject.assistedinject.Assisted
import com.google.inject.assistedinject.AssistedInject
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.expression.CompileTimeExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.pipeline.utils.sourceError
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.EvaluationFailureException
import hu.bme.mit.semantifyr.oxsts.lang.semantics.modality.ExpressionModalityEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.modality.Modality
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.ExpressionTypeEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.TypeCompatibility
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.TypeEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.utils.ExpressionVisitor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticBinaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CastExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfThenElse
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInfinity
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralNothing
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralReal
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralString
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NegationOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ParameterDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RangeExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RecordDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RecordLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SelfReference
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

class ExpressionCallInliner @AssistedInject constructor(
    @param:Assisted val instance: Instance,
    private val compilationArtifactManager: CompilationArtifactManager,
    private val expressionCallExpander: ExpressionCallExpander,
    private val expressionTypeEvaluatorProvider: ExpressionTypeEvaluatorProvider,
    private val typeCompatibility: TypeCompatibility,
    private val compileTimeExpressionEvaluatorProvider: CompileTimeExpressionEvaluatorProvider,
    private val expressionModalityEvaluatorProvider: ExpressionModalityEvaluatorProvider,
) : ExpressionVisitor<Unit>() {

    private val processorQueue = ArrayDeque<Expression>()
    private val dispatches = mutableMapOf<EObject, MutableList<Dispatch>>()

    // returns the condition under which every holder the expression reads through is present, null when it needs none
    fun process(expression: Expression): Expression? {
        val container = expression.eContainer()
        val containingFeature = expression.eContainingFeature()
        check(!containingFeature.isMany) {
            "A processed expression is held by a single-valued feature"
        }
        dispatches.clear()
        visit(expression)
        processAll()
        return presenceConditionOf(container.eGet(containingFeature) as EObject)
    }

    fun processWriteTarget(target: Expression): Expression? {
        if (expressionCallExpander.isThroughRuntimeHolder(target, instance)) {
            sourceError(target, "Writing through a variable is not supported.")
        }
        return process(target)
    }

    private fun presenceConditionOf(element: EObject): Expression? {
        val elementDispatches = dispatches[element].orEmpty()
        val conditions = mutableListOf<Expression>()
        for (dispatch in elementDispatches) {
            val holderPresence = dispatch.holderPresence ?: continue
            presenceConditionOf(holderPresence)?.let {
                conditions += it
            }
            conditions += holderPresence
        }
        val lastDispatch = elementDispatches.lastOrNull()
        val rest = if (lastDispatch != null && lastDispatch.branchCount > 1) {
            branchPresenceConditionOf(element as Expression, lastDispatch.branchCount)
        } else {
            conjunctionOrNull(
                element.eContents().mapNotNull {
                    presenceConditionOf(it)
                },
            )
        }
        rest?.let {
            conditions += it
        }
        return conjunctionOrNull(conditions)
    }

    // a branch's holders are needed only when the dispatch takes the branch
    private fun branchPresenceConditionOf(dispatched: Expression, branchCount: Int): Expression? {
        val guards = mutableListOf<Expression>()
        val branches = mutableListOf<Expression>()
        var current = dispatched
        repeat(branchCount - 1) {
            val conditional = current as? IfThenElse ?: error("A dispatch over $branchCount candidates is not a chain of conditionals")
            guards += conditional.guard
            branches += conditional.then
            current = conditional.`else`
        }
        branches += current

        val guardConditions = guards.mapNotNull {
            presenceConditionOf(it)
        }
        val branchConditions = branches.map {
            presenceConditionOf(it)
        }
        if (branchConditions.all { it == null }) {
            return conjunctionOrNull(guardConditions)
        }
        var chained = branchConditions.last() ?: OxstsFactory.createLiteralBoolean(true)
        for (index in guards.indices.reversed()) {
            val branchCondition = branchConditions[index] ?: OxstsFactory.createLiteralBoolean(true)
            val elseCondition = chained
            chained = OxstsFactory.createIfThenElse().also {
                it.guard = guards[index].copy()
                it.then = branchCondition
                it.`else` = elseCondition
            }
        }
        return conjunctionOrNull(guardConditions + chained)
    }

    private fun conjunctionOrNull(conditions: List<Expression>): Expression? {
        return conditions.reduceOrNull { left, right ->
            OxstsFactory.createBooleanOperator(BooleanOp.AND, left, right)
        }
    }

    private fun replace(expression: Expression, replacement: Expression) {
        EcoreUtil2.replace(expression, replacement)
        val replacedDispatches = dispatches.remove(expression) ?: return
        dispatches.getOrPut(replacement) {
            mutableListOf()
        }.addAll(0, replacedDispatches)
    }

    private fun recordDispatch(expanded: ExpandedExpression) {
        val dispatch = expanded.dispatch ?: return
        if (dispatch.holderPresence == null && dispatch.branchCount == 1) {
            return
        }
        dispatches.getOrPut(expanded.expression) {
            mutableListOf()
        } += dispatch
        dispatch.holderPresence?.let {
            processorQueue.addFirst(it)
        }
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

    override fun visit(expression: RecordLiteral) {
        for (index in expression.fields.indices.reversed()) {
            processorQueue.addFirst(expression.fields[index].expression)
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
        if (dispatchMemberRead(expression)) {
            return
        }
        if (expression.member.eContainer() is RecordDeclaration) {
            processorQueue.addFirst(expression.primary)
            return
        }
        rewriteFeatureReference(expression)
    }

    override fun visit(expression: CallSuffixExpression) {
        val expanded = expressionCallExpander.expandCall(expression, instance)
        replace(expression, expanded.expression)
        recordDispatch(expanded)
        processorQueue.addFirst(expanded.expression)

        compilationArtifactManager.commitStep(CompilationPass.ExpressionCallInlining)
    }

    override fun visit(expression: IndexingSuffixExpression) {
        if (dispatchMemberRead(expression)) {
            return
        }
        processorQueue.addFirst(expression.index)
    }

    private fun dispatchMemberRead(expression: Expression): Boolean {
        val dispatched = expressionCallExpander.dispatchMemberReadOrNull(expression, instance) ?: return false
        replace(expression, dispatched.expression)
        recordDispatch(dispatched)
        processorQueue.addFirst(dispatched.expression)
        compilationArtifactManager.commitStep(CompilationPass.ExpressionCallInlining)
        return true
    }

    override fun visit(expression: CastExpression) {
        val bodyType = expressionTypeEvaluatorProvider.evaluate(expression.body)
        val typeSpec = expression.typespecification ?: sourceError(expression, "Cast expression has no target type specification.")
        val castType = expressionTypeEvaluatorProvider.getEvaluator(expression).fromTypeSpecification(typeSpec)
        checkCastIsNarrowing(expression, bodyType, castType)

        processorQueue.addFirst(expression.body)
        replace(expression, expression.body)
    }

    private fun checkCastIsNarrowing(
        expression: CastExpression,
        bodyType: TypeEvaluation,
        castType: TypeEvaluation,
    ) {
        if (!typeCompatibility.isAssignable(bodyType, castType, expression)) {
            sourceError(
                expression,
                "Cast from ${formatType(bodyType)} to ${formatType(castType)} is not a valid narrowing cast (widening or unrelated types are rejected).",
            )
        }
    }

    private fun formatType(type: TypeEvaluation): String {
        return type.domain?.name ?: "<unknown>"
    }

    override fun visit(expression: IfThenElse) {
        val decision = decisionOrNull(expression.guard)
        if (decision != null) {
            val taken = if (decision) {
                expression.then
            } else {
                expression.`else`
            }
            replace(expression, taken)
            processorQueue.addFirst(taken)
            return
        }
        processorQueue.addFirst(expression.guard)
        processorQueue.addFirst(expression.then)
        processorQueue.addFirst(expression.`else`)
    }

    private fun decisionOrNull(guard: Expression): Boolean? {
        if (expressionModalityEvaluatorProvider.evaluate(guard) == Modality.RUNTIME) {
            return null
        }
        val readsParameter = (listOf(guard) + guard.eAllOfType<ElementReference>().toList()).any {
            it is ElementReference && it.element is ParameterDeclaration
        }
        if (readsParameter) {
            return null
        }
        return try {
            compileTimeExpressionEvaluatorProvider.getEvaluator(instance).evaluateBoolean(guard)
        } catch (_: EvaluationFailureException) {
            null
        }
    }

    private fun rewriteFeatureReference(expression: Expression) {
        val rewritten = expressionCallExpander.expandFeatureReferenceOrNull(expression, instance) ?: return
        replace(expression, rewritten)
        compilationArtifactManager.commitStep(CompilationPass.ExpressionCallInlining)
    }

    interface Factory {
        fun create(instance: Instance): ExpressionCallInliner
    }

}
