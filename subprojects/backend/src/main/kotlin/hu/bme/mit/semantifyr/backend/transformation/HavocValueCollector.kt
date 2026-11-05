/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.transformation

import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.xtext.EcoreUtil2

@VerificationScoped
class HavocValueCollector {
    private val cache = mutableMapOf<VariableDeclaration, List<Int>>()

    fun valuesFor(variable: VariableDeclaration): List<Int> {
        return cache.getOrPut(variable) {
            compute(variable)
        }
    }

    // the candidates flank every literal the variable is compared with, so they cover all values such comparisons tell apart
    fun isComplete(variable: VariableDeclaration): Boolean {
        val root = EcoreUtil2.getContainerOfType(variable, InlinedOxsts::class.java) ?: return false
        return root.eAllContents().asSequence().filterIsInstance<ElementReference>().filter {
            it.element === variable
        }.all {
            isWrite(it) || isLiteralComparison(it)
        }
    }

    private fun isWrite(reference: ElementReference): Boolean {
        return when (val container = reference.eContainer()) {
            is AssignmentOperation -> container.reference === reference
            is HavocOperation -> container.reference === reference
            else -> false
        }
    }

    private fun isLiteralComparison(reference: ElementReference): Boolean {
        val comparison = reference.eContainer() as? ComparisonOperator ?: return false
        val other = if (comparison.left === reference) {
            comparison.right
        } else {
            comparison.left
        }
        val literal = other.asInt() ?: return false
        return literal != Int.MIN_VALUE && literal != Int.MAX_VALUE
    }

    private fun compute(variable: VariableDeclaration): List<Int> {
        val root = EcoreUtil2.getContainerOfType(variable, InlinedOxsts::class.java) ?: return listOf(0)
        val values = sortedSetOf<Int>()

        variable.expression?.asInt()?.let {
            values += it
        }

        for (comparison in root.eAllContents().asSequence().filterIsInstance<ComparisonOperator>()) {
            collectFromComparison(comparison, variable, values)
        }

        for (literal in root.eAllContents().asSequence().filterIsInstance<LiteralInteger>()) {
            values += literal.value
            literal.eContainer().let {
                if (it is ArithmeticUnaryOperator && it.op == UnaryOp.MINUS) {
                    values += -literal.value
                }
            }
        }

        if (values.isEmpty()) {
            values += 0
        }
        return values.toList()
    }

    private fun collectFromComparison(
        comparison: ComparisonOperator,
        variable: VariableDeclaration,
        out: MutableSet<Int>,
    ) {
        val left = comparison.left
        val right = comparison.right
        val variableOnLeft = left.referencesVariable(variable)
        val variableOnRight = right.referencesVariable(variable)
        if (variableOnLeft == variableOnRight) {
            return
        }

        val literal = if (variableOnLeft) {
            right.asInt()
        } else {
            left.asInt()
        }
        if (literal == null) {
            return
        }

        val (valid, invalid) = when (comparison.op) {
            ComparisonOp.EQ -> literal to literal + 1
            ComparisonOp.NOT_EQ -> literal + 1 to literal
            ComparisonOp.LESS -> {
                if (variableOnLeft) {
                    literal - 1 to literal
                } else {
                    literal + 1 to literal
                }
            }
            ComparisonOp.LESS_EQ -> {
                if (variableOnLeft) {
                    literal to literal + 1
                } else {
                    literal to literal - 1
                }
            }
            ComparisonOp.GREATER -> {
                if (variableOnLeft) {
                    literal + 1 to literal
                } else {
                    literal - 1 to literal
                }
            }
            ComparisonOp.GREATER_EQ -> {
                if (variableOnLeft) {
                    literal to literal - 1
                } else {
                    literal to literal + 1
                }
            }
        }

        out += valid
        out += invalid
        // an equality cuts on both sides of its literal
        if (comparison.op == ComparisonOp.EQ || comparison.op == ComparisonOp.NOT_EQ) {
            out += literal - 1
        }
    }

    private fun Expression.referencesVariable(variable: VariableDeclaration): Boolean {
        return this is ElementReference && element === variable
    }

    private fun Expression.asInt(): Int? {
        return when (this) {
            is LiteralInteger -> value
            is ArithmeticUnaryOperator -> {
                val inner = body.asInt() ?: return null
                when (op) {
                    UnaryOp.PLUS -> inner
                    UnaryOp.MINUS -> -inner
                }
            }
            else -> null
        }
    }

}
