/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.flattening

import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.pipeline.utils.sourceError
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BooleanOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfThenElse
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RecordDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.xtext.EcoreUtil2

class RecordFlatteningPass {

    companion object {
        const val FIELD_SEPARATOR = "$"
    }

    fun flattenRecords(inlinedOxsts: InlinedOxsts): Map<VariableDeclaration, Map<List<VariableDeclaration>, VariableDeclaration>> {
        val recordVariables = inlinedOxsts.eAllOfType<VariableDeclaration>().filter {
            it.typeSpecification?.domain is RecordDeclaration
        }.toList()
        if (recordVariables.isEmpty()) {
            return emptyMap()
        }

        val fieldVariables = recordVariables.associateWith {
            createFieldVariables(it)
        }
        RecordRewriting(fieldVariables).rewrite(inlinedOxsts)
        return fieldVariables
    }

    private fun createFieldVariables(recordVariable: VariableDeclaration): Map<List<VariableDeclaration>, VariableDeclaration> {
        val record = recordVariable.typeSpecification.domain as RecordDeclaration
        return leafPaths(record).associateWith { path ->
            val fieldVariable = if (recordVariable is LocalVarDeclarationOperation) {
                OxstsFactory.createLocalVarDeclarationOperation()
            } else {
                OxstsFactory.createVariableDeclaration().also {
                    it.annotation = OxstsFactory.createAnnotationContainer()
                    for (annotated in listOf(recordVariable) + path) {
                        annotated.annotation?.annotations?.mapTo(it.annotation.annotations) { annotation ->
                            annotation.copy()
                        }
                    }
                }
            }
            fieldVariable.also {
                it.name = recordVariable.name + path.joinToString("") { field ->
                    "$FIELD_SEPARATOR${field.name}"
                }
                it.typeSpecification = path.last().typeSpecification.copy()
            }
        }
    }

}

private fun leafPaths(record: RecordDeclaration): List<List<VariableDeclaration>> {
    return record.members.filterIsInstance<VariableDeclaration>().flatMap { field ->
        val fieldRecord = field.typeSpecification.domain as? RecordDeclaration
        if (fieldRecord == null) {
            listOf(listOf(field))
        } else {
            leafPaths(fieldRecord).map {
                listOf(field) + it
            }
        }
    }
}

private fun isRecordField(element: Any?): Boolean {
    return element is VariableDeclaration && element.eContainer() is RecordDeclaration
}

private class RecordRewriting(
    private val fieldVariables: Map<VariableDeclaration, Map<List<VariableDeclaration>, VariableDeclaration>>,
) {

    fun rewrite(inlinedOxsts: InlinedOxsts) {
        rewriteFieldAccesses(inlinedOxsts)

        for (comparison in inlinedOxsts.eAllOfType<ComparisonOperator>().toList().asReversed()) {
            if (recordTypeOf(comparison.left) != null || recordTypeOf(comparison.right) != null) {
                EcoreUtil2.replace(comparison, compareFields(comparison))
            }
        }
        for (assignment in inlinedOxsts.eAllOfType<AssignmentOperation>().toList()) {
            if (recordTypeOf(assignment.reference) != null) {
                splice(assignment, assignFields(assignment))
            }
        }
        for (havoc in inlinedOxsts.eAllOfType<HavocOperation>().toList()) {
            if (recordTypeOf(havoc.reference) != null) {
                splice(havoc, havocFields(havoc))
            }
        }
        for (declaration in inlinedOxsts.eAllOfType<LocalVarDeclarationOperation>().toList()) {
            if (declaration in fieldVariables) {
                splice(declaration, declareFields(declaration))
            }
        }
        for ((recordVariable, fields) in fieldVariables) {
            if (recordVariable.eContainer() is InlinedOxsts) {
                val variables = inlinedOxsts.variables
                val index = variables.indexOf(recordVariable)
                variables.addAll(index + 1, initializeFields(recordVariable, fields))
                variables.remove(recordVariable)
            }
        }

        val leftover = inlinedOxsts.eAllOfType<ElementReference>().firstOrNull {
            it.element in fieldVariables
        }
        if (leftover != null) {
            sourceError(leftover, "A record value can only be read field by field, assigned, compared with == or !=, or havoced.")
        }
    }

    private fun rewriteFieldAccesses(inlinedOxsts: InlinedOxsts) {
        val references = inlinedOxsts.eAllOfType<ElementReference>().filter {
            it.element in fieldVariables
        }.toList()
        for (reference in references) {
            var access: Expression = reference
            while (true) {
                val parent = access.eContainer()
                if (parent !is NavigationSuffixExpression || parent.primary !== access || !isRecordField(parent.member)) {
                    break
                }
                access = parent
            }
            if (recordTypeOf(access) == null) {
                val (recordVariable, path) = recordPathOf(access)
                EcoreUtil2.replace(access, OxstsFactory.createElementReference(fieldVariables.getValue(recordVariable).getValue(path)))
            }
        }
    }

    private fun recordTypeOf(expression: Expression): RecordDeclaration? {
        return when (expression) {
            is ElementReference -> (expression.element as? VariableDeclaration)?.takeIf {
                it in fieldVariables
            }?.typeSpecification?.domain as? RecordDeclaration
            is NavigationSuffixExpression -> (expression.member as? VariableDeclaration)?.takeIf {
                isRecordField(it)
            }?.typeSpecification?.domain as? RecordDeclaration
            is IfThenElse -> recordTypeOf(expression.then)
            else -> null
        }
    }

    private fun recordPathOf(expression: Expression): Pair<VariableDeclaration, List<VariableDeclaration>> {
        return when (expression) {
            is ElementReference -> (expression.element as VariableDeclaration) to emptyList()
            is NavigationSuffixExpression -> {
                val (recordVariable, path) = recordPathOf(expression.primary)
                recordVariable to path + (expression.member as VariableDeclaration)
            }
            else -> sourceError(expression, "Expected a record variable or one of its fields.")
        }
    }

    private fun fieldExpressions(expression: Expression): List<Expression> {
        if (expression is IfThenElse) {
            return fieldExpressions(expression.then).zip(fieldExpressions(expression.`else`)) { then, otherwise ->
                OxstsFactory.createIfThenElse().also {
                    it.guard = expression.guard.copy()
                    it.then = then
                    it.`else` = otherwise
                }
            }
        }
        val record = recordTypeOf(expression) ?: sourceError(expression, "Expected a record value.")
        val (recordVariable, prefix) = recordPathOf(expression)
        val fields = fieldVariables.getValue(recordVariable)
        return leafPaths(record).map {
            OxstsFactory.createElementReference(fields.getValue(prefix + it))
        }
    }

    private fun compareFields(comparison: ComparisonOperator): Expression {
        val fieldComparisons = fieldExpressions(comparison.left).zip(fieldExpressions(comparison.right)) { left, right ->
            OxstsFactory.createComparisonOperator().also {
                it.op = comparison.op
                it.left = left
                it.right = right
            }
        }
        return when (comparison.op) {
            ComparisonOp.EQ -> fieldComparisons.reduceOrNull<Expression, Expression> { left, right ->
                OxstsFactory.createBooleanOperator(BooleanOp.AND, left, right)
            } ?: OxstsFactory.createLiteralBoolean(true)
            ComparisonOp.NOT_EQ -> fieldComparisons.reduceOrNull<Expression, Expression> { left, right ->
                OxstsFactory.createBooleanOperator(BooleanOp.OR, left, right)
            } ?: OxstsFactory.createLiteralBoolean(false)
            else -> sourceError(comparison, "Records can only be compared with == and !=.")
        }
    }

    private fun assignFields(assignment: AssignmentOperation): List<Operation> {
        return fieldExpressions(assignment.reference).zip(fieldExpressions(assignment.expression)) { target, value ->
            OxstsFactory.createAssignmentOperation().also {
                it.reference = target
                it.expression = value
            }
        }
    }

    private fun havocFields(havoc: HavocOperation): List<Operation> {
        return fieldExpressions(havoc.reference).map { target ->
            OxstsFactory.createHavocOperation().also {
                it.reference = target
            }
        }
    }

    private fun declareFields(declaration: LocalVarDeclarationOperation): List<Operation> {
        return initializeFields(declaration, fieldVariables.getValue(declaration)).map {
            it as LocalVarDeclarationOperation
        }
    }

    private fun initializeFields(
        recordVariable: VariableDeclaration,
        fields: Map<List<VariableDeclaration>, VariableDeclaration>,
    ): List<VariableDeclaration> {
        val initializer = recordVariable.expression
        val values = initializer?.let {
            fieldExpressions(it)
        }
        return fields.entries.mapIndexed { index, (path, fieldVariable) ->
            fieldVariable.expression = values?.get(index) ?: path.last().expression?.copy()
            fieldVariable
        }
    }

    private fun splice(operation: Operation, replacement: List<Operation>) {
        val sequence = operation.eContainer() as? SequenceOperation ?: sourceError(operation, "Expected the operation in a sequence.")
        val index = sequence.steps.indexOf(operation)
        sequence.steps.addAll(index + 1, replacement)
        sequence.steps.remove(operation)
    }

}
