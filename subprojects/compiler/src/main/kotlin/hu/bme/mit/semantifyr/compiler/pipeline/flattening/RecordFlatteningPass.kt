/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.flattening

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.LocalVariableManager
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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RecordLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.xtext.EcoreUtil2

class RecordFlatteningPass @Inject constructor(
    private val localVariableManager: LocalVariableManager,
) {

    companion object {
        const val FIELD_SEPARATOR = "$"
    }

    fun flattenRecords(inlinedOxsts: InlinedOxsts): Map<VariableDeclaration, Map<List<VariableDeclaration>, VariableDeclaration>> {
        val recordVariables = inlinedOxsts.eAllOfType<VariableDeclaration>().filter {
            it.typeSpecification?.domain is RecordDeclaration
        }.toList()
        if (recordVariables.isEmpty() && inlinedOxsts.eAllOfType<RecordLiteral>().none()) {
            return emptyMap()
        }

        val fieldVariables = recordVariables.associateWith {
            createFieldVariables(it)
        }
        RecordRewriting(fieldVariables, localVariableManager).rewrite(inlinedOxsts)
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

private fun recordFields(record: RecordDeclaration): List<VariableDeclaration> {
    return record.members.filterIsInstance<VariableDeclaration>()
}

private fun leafPaths(record: RecordDeclaration): List<List<VariableDeclaration>> {
    return recordFields(record).flatMap { field ->
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

// every list of field expressions follows the leaf order of `leafPaths`
private class RecordRewriting(
    private val fieldVariables: Map<VariableDeclaration, Map<List<VariableDeclaration>, VariableDeclaration>>,
    private val localVariableManager: LocalVariableManager,
) {

    fun rewrite(inlinedOxsts: InlinedOxsts) {
        rewriteFieldReads(inlinedOxsts)

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
        val leftoverLiteral = inlinedOxsts.eAllOfType<RecordLiteral>().firstOrNull()
        if (leftoverLiteral != null) {
            sourceError(leftoverLiteral, "A record literal can only be read field by field, assigned or compared with == or !=.")
        }
    }

    // descendants first, so a read inside another read's record value is already a scalar when that value is copied
    private fun rewriteFieldReads(inlinedOxsts: InlinedOxsts) {
        val reads = inlinedOxsts.eAllOfType<NavigationSuffixExpression>().filter {
            isRecordField(it.member) && recordTypeOf(it) == null
        }.toList().asReversed()
        for (read in reads) {
            val record = recordTypeOf(read.primary) ?: sourceError(read, "Expected a record value.")
            val index = leafPaths(record).indexOf(listOf(read.member as VariableDeclaration))
            EcoreUtil2.replace(read, fieldExpressions(read.primary)[index])
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
            is RecordLiteral -> expression.record
            else -> null
        }
    }

    private fun fieldExpressions(expression: Expression): List<Expression> {
        return when (expression) {
            is IfThenElse -> fieldExpressions(expression.then).zip(fieldExpressions(expression.`else`)) { then, otherwise ->
                OxstsFactory.createIfThenElse().also {
                    it.guard = expression.guard.copy()
                    it.then = then
                    it.`else` = otherwise
                }
            }
            is RecordLiteral -> literalFieldExpressions(expression)
            is ElementReference -> {
                val fields = fieldVariables[expression.element] ?: sourceError(expression, "Expected a record value.")
                fields.values.map {
                    OxstsFactory.createElementReference(it)
                }
            }
            is NavigationSuffixExpression -> {
                val record = recordTypeOf(expression.primary) ?: sourceError(expression, "Expected a record value.")
                val paths = leafPaths(record)
                fieldExpressions(expression.primary).filterIndexed { index, _ ->
                    paths[index].first() == expression.member
                }
            }
            else -> sourceError(expression, "Expected a record value.")
        }
    }

    private fun literalFieldExpressions(literal: RecordLiteral): List<Expression> {
        return recordFields(literal.record).flatMap { field ->
            val value = literal.fields.firstOrNull {
                it.field == field
            }?.expression ?: field.expression
            val fieldRecord = field.typeSpecification.domain as? RecordDeclaration
            when {
                value == null && fieldRecord != null -> defaultValues(fieldRecord).map {
                    it ?: sourceError(literal, "The record literal leaves a field of '${field.name}' without a value.")
                }
                value == null -> sourceError(literal, "The record literal leaves '${field.name}' without a value.")
                fieldRecord != null -> fieldExpressions(value)
                else -> listOf(value.copy())
            }
        }
    }

    // a field without a default is null, and the variable that holds it is havoced
    private fun defaultValues(record: RecordDeclaration): List<Expression?> {
        return recordFields(record).flatMap { field ->
            val fieldRecord = field.typeSpecification.domain as? RecordDeclaration
            val default = field.expression
            when {
                fieldRecord == null -> listOf(default?.copy())
                default != null -> fieldExpressions(default)
                else -> defaultValues(fieldRecord)
            }
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

    // the whole value is read before any field is written, so a value that reads a field written before it is saved first
    private fun assignFields(assignment: AssignmentOperation): List<Operation> {
        val written = mutableSetOf<VariableDeclaration>()
        val saved = mutableListOf<Operation>()
        val assignments = fieldExpressions(assignment.reference).zip(fieldExpressions(assignment.expression)) { target, value ->
            val field = (target as ElementReference).element as VariableDeclaration
            val source = if (readsAny(value, written)) {
                val temporary = OxstsFactory.createLocalVarDeclarationOperation().also {
                    it.name = field.name
                    it.typeSpecification = field.typeSpecification.copy()
                    it.expression = value
                }
                temporary.name = localVariableManager.allocateInlinedName(temporary)
                saved += temporary
                OxstsFactory.createElementReference(temporary)
            } else {
                value
            }
            written += field
            OxstsFactory.createAssignmentOperation().also {
                it.reference = target
                it.expression = source
            }
        }
        return saved + assignments
    }

    private fun readsAny(expression: Expression, variables: Set<VariableDeclaration>): Boolean {
        if (expression is ElementReference && expression.element in variables) {
            return true
        }
        return expression.eAllOfType<ElementReference>().any {
            it.element in variables
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
        val values = recordVariable.expression?.let {
            fieldExpressions(it)
        } ?: defaultValues(recordVariable.typeSpecification.domain as RecordDeclaration)
        return fields.values.mapIndexed { index, fieldVariable ->
            fieldVariable.expression = values[index]
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
