/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.flattening

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.pipeline.utils.sourceError
import hu.bme.mit.semantifyr.oxsts.lang.semantics.MultiplicityRangeEvaluator
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.EvaluationFailureException
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.IntegerEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.RangeEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

class ArrayFlatteningPass @Inject constructor(
    private val multiplicityRangeEvaluator: MultiplicityRangeEvaluator,
    private val constantExpressionEvaluatorProvider: ConstantExpressionEvaluatorProvider,
) {

    fun flattenArrays(inlinedOxsts: InlinedOxsts): Int {
        val arrayVariables = collectArrayVariables(inlinedOxsts)
        for (variable in arrayVariables) {
            flattenVariable(variable)
        }
        return arrayVariables.size
    }

    private fun collectArrayVariables(inlinedOxsts: InlinedOxsts): List<VariableDeclaration> {
        return inlinedOxsts.eAllOfType<VariableDeclaration>().filter {
            val typeSpec = it.typeSpecification ?: return@filter false
            val range = multiplicityRangeEvaluator.evaluate(typeSpec)
            val upper = range.upperBound

            upper != RangeEvaluation.INFINITY && upper > 1
        }.toList()
    }

    private fun flattenVariable(arrayVariable: VariableDeclaration) {
        val typeSpec = arrayVariable.typeSpecification ?: return
        val range = multiplicityRangeEvaluator.evaluate(typeSpec)
        val slotCount = range.upperBound
        if (slotCount <= 0) {
            return
        }

        val elementDomain = typeSpec.domain ?: sourceError(arrayVariable, "Array variable '${arrayVariable.name}' has no element domain.")

        val slotVariables = createSlotVariables(arrayVariable, elementDomain, slotCount)

        val root = EcoreUtil2.getRootContainer(arrayVariable)
        rewriteIndexingReferences(root, arrayVariable, slotVariables)

        insertSlotsAndRemoveOriginal(arrayVariable, slotVariables)
    }

    private fun createSlotVariables(
        arrayVariable: VariableDeclaration,
        elementDomain: DomainDeclaration,
        slotCount: Int,
    ): List<VariableDeclaration> {
        val initializer = arrayVariable.expression
        val slotInitializers = splitInitializer(arrayVariable, initializer, slotCount)

        return (0 until slotCount).map {
            val slot = arrayVariable.copy()
            slot.name = "${arrayVariable.name}_$it"
            slot.typeSpecification = OxstsFactory.createTypeSpecification().also { typeSpec ->
                typeSpec.domain = elementDomain
            }
            slot.expression = slotInitializers[it] as Expression?
            slot
        }
    }

    private fun splitInitializer(
        arrayVariable: VariableDeclaration,
        initializer: EObject?,
        slotCount: Int,
    ): List<EObject?> {
        return when (initializer) {
            null -> List(slotCount) { null }
            is ArrayLiteral -> {
                val values = initializer.values
                if (values.size > slotCount) {
                    sourceError(
                        arrayVariable,
                        "Array literal has ${values.size} values but the array holds only $slotCount slots.",
                    )
                }
                List(slotCount) {
                    if (it < values.size) {
                        values[it].copy()
                    } else {
                        null
                    }
                }
            }
            else -> sourceError(
                arrayVariable,
                "Array initializer must be an array literal (got ${initializer::class.simpleName}).",
            )
        }
    }

    private fun rewriteIndexingReferences(
        root: EObject,
        arrayVariable: VariableDeclaration,
        slotVariables: List<VariableDeclaration>,
    ) {
        val allIndexings = EcoreUtil2.eAllOfType(root, IndexingSuffixExpression::class.java).filter {
            referencesArrayVariable(it.primary, arrayVariable)
        }.toList()
        val (writes, reads) = allIndexings.partition {
            isWriteSite(it)
        }
        for (indexing in reads) {
            rewriteIndexing(indexing, arrayVariable, slotVariables)
        }
        for (indexing in writes) {
            rewriteIndexing(indexing, arrayVariable, slotVariables)
        }
    }

    private fun isWriteSite(indexing: IndexingSuffixExpression): Boolean {
        val parent = indexing.eContainer()
        return parent is AssignmentOperation && parent.reference === indexing
    }

    private fun referencesArrayVariable(
        expression: EObject?,
        variable: VariableDeclaration,
    ): Boolean {
        return when (expression) {
            is ElementReference -> expression.element === variable
            is NavigationSuffixExpression -> expression.member === variable
            else -> false
        }
    }

    private fun rewriteIndexing(
        indexing: IndexingSuffixExpression,
        arrayVariable: VariableDeclaration,
        slotVariables: List<VariableDeclaration>,
    ) {
        val indexValue = evaluateConstantInteger(indexing.index)
        if (indexValue != null) {
            rewriteConstantIndexing(indexing, arrayVariable, slotVariables, indexValue)
        } else {
            rewriteRuntimeIndexing(indexing, slotVariables)
        }
    }

    private fun rewriteConstantIndexing(
        indexing: IndexingSuffixExpression,
        arrayVariable: VariableDeclaration,
        slotVariables: List<VariableDeclaration>,
        indexValue: Int,
    ) {
        if (indexValue < 0 || indexValue >= slotVariables.size) {
            sourceError(
                indexing,
                "Array index $indexValue is out of bounds for '${arrayVariable.name}' (size ${slotVariables.size}).",
            )
        }
        val replacement = slotRead(indexing, slotVariables[indexValue])
        EcoreUtil2.replace(indexing, replacement)
    }

    private fun rewriteRuntimeIndexing(
        indexing: IndexingSuffixExpression,
        slotVariables: List<VariableDeclaration>,
    ) {
        val parent = indexing.eContainer()
        if (parent is AssignmentOperation && parent.reference === indexing) {
            rewriteRuntimeIndexWrite(parent, indexing, slotVariables)
        } else {
            rewriteRuntimeIndexRead(indexing, slotVariables)
        }
    }

    private fun rewriteRuntimeIndexRead(
        indexing: IndexingSuffixExpression,
        slotVariables: List<VariableDeclaration>,
    ) {
        val indexExpr = indexing.index ?: sourceError(indexing, "Array indexing has no index expression.")
        // Build right-to-left: start with the last slot as the final else
        // branch, then prepend `if i == k then a_k else <rest>` for
        // k = N-2 down to 0.
        var chain: Expression = slotRead(indexing, slotVariables.last())
        for (k in slotVariables.size - 2 downTo 0) {
            val thenExpr = slotRead(indexing, slotVariables[k])
            chain = OxstsFactory.createIfThenElse().also {
                it.guard = buildEqGuard(indexExpr, k)
                it.then = thenExpr
                it.`else` = chain
            }
        }
        EcoreUtil2.replace(indexing, chain)
    }

    private fun rewriteRuntimeIndexWrite(
        assignment: AssignmentOperation,
        indexing: IndexingSuffixExpression,
        slotVariables: List<VariableDeclaration>,
    ) {
        val indexExpr = indexing.index ?: sourceError(indexing, "Array indexing has no index expression.")
        val rightHandSide = assignment.expression ?: sourceError(assignment, "Assignment has no right-hand side.")

        var elseOp: Operation = slotWrite(slotVariables.last(), rightHandSide.copy())
        for (k in slotVariables.size - 2 downTo 0) {
            val body = OxstsFactory.createSequenceOperation().also {
                it.steps += slotWrite(slotVariables[k], rightHandSide.copy())
            }
            val elseBranch = OxstsFactory.createSequenceOperation().also {
                it.steps += elseOp
            }
            elseOp = OxstsFactory.createIfOperation().also {
                it.guard = buildEqGuard(indexExpr, k)
                it.body = body
                it.`else` = elseBranch
            }
        }
        EcoreUtil2.replace(assignment, elseOp)
    }

    private fun buildEqGuard(
        indexExpr: Expression,
        k: Int,
    ): Expression {
        return OxstsFactory.createComparisonOperator().also {
            it.op = ComparisonOp.EQ
            it.left = indexExpr.copy()
            it.right = OxstsFactory.createLiteralInteger(k)
        }
    }

    private fun slotRead(
        indexing: IndexingSuffixExpression,
        slot: VariableDeclaration,
    ): Expression {
        return when (val primary = indexing.primary) {
            is ElementReference -> OxstsFactory.createElementReference(slot)
            is NavigationSuffixExpression -> OxstsFactory.createNavigationSuffixExpression().also {
                it.primary = primary.primary.copy()
                it.member = slot
                it.isOptional = primary.isOptional
            }
            else -> sourceError(
                indexing,
                "Unexpected indexing primary shape: ${primary::class.simpleName}",
            )
        }
    }

    private fun slotWrite(
        slot: VariableDeclaration,
        rhs: Expression,
    ): Operation {
        return OxstsFactory.createAssignmentOperation().also {
            it.reference = OxstsFactory.createElementReference(slot)
            it.expression = rhs
        }
    }

    private fun evaluateConstantInteger(expression: EObject?): Int? {
        if (expression == null) {
            return null
        }
        return try {
            val evaluation = constantExpressionEvaluatorProvider.evaluate(expression as Expression) as? IntegerEvaluation
            evaluation?.value
        } catch (_: EvaluationFailureException) {
            null
        }
    }

    private fun insertSlotsAndRemoveOriginal(
        arrayVariable: VariableDeclaration,
        slotVariables: List<VariableDeclaration>,
    ) {
        val container = arrayVariable.eContainer()
        when (container) {
            is InlinedOxsts -> {
                val list = container.variables
                val index = list.indexOf(arrayVariable)
                list.addAll(index + 1, slotVariables)
                list.remove(arrayVariable)
            }
            else -> sourceError(
                arrayVariable,
                "Unexpected container for array variable '${arrayVariable.name}': ${container::class.simpleName}",
            )
        }
    }
}
