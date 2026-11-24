/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.utils

import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticBinaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralReal
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.util.EcoreUtil

// until flattening adds the assumption that blocks its operation, a fold must not erase an operand without a value
fun Expression.mayHaveNoValue(): Boolean {
    return (sequenceOf(this) + eAllOfType<Expression>()).any {
        it is IndexingSuffixExpression || (it is ArithmeticBinaryOperator && it.isDivisionByPossibleZero())
    }
}

fun ArithmeticBinaryOperator.isDivisionByPossibleZero(): Boolean {
    if (op != ArithmeticOp.DIV) {
        return false
    }
    return when (val divisor = right) {
        is LiteralInteger -> divisor.value == 0
        is LiteralReal -> divisor.value == 0.0
        else -> true
    }
}

// the property and the variable declarations are evaluated by no operation, so they get no assumption
fun Expression.assumeBeforeEvaluation(condition: Expression): Boolean {
    val operation = generateSequence<EObject>(this) {
        it.eContainer()
    }.firstOrNull {
        it is Operation
    } ?: return false
    val sequence = operation.eContainer() as? SequenceOperation ?: sourceError(operation, "Expected the operation in a sequence.")
    val index = sequence.steps.indexOf(operation)
    val alreadyAssumed = sequence.steps.subList(0, index).asReversed().asSequence().takeWhile {
        it is AssumptionOperation
    }.any {
        EcoreUtil.equals((it as AssumptionOperation).expression, condition)
    }
    if (alreadyAssumed) {
        return false
    }
    sequence.steps.add(index, OxstsFactory.createAssumptionOperation(condition))
    return true
}
