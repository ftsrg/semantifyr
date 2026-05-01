/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.utils

import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaCompileTimeExpressionEvaluator
import hu.bme.mit.semantifyr.compiler.pipeline.expression.evaluateTyped
import hu.bme.mit.semantifyr.compiler.pipeline.expression.tryEvaluateTypedOrNull
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject

fun EObject.variableReadExpressions(evaluator: MetaCompileTimeExpressionEvaluator): Sequence<Expression> {
    val selfRead = if (
        this is Expression &&
        !OxstsUtils.isWriteExpression(this) &&
        evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, this) != null
    ) {
        sequenceOf(this)
    } else {
        emptySequence()
    }
    val descendants = eAllOfType<Expression>().filterNot {
        OxstsUtils.isWriteExpression(it)
    }.filter {
        evaluator.tryEvaluateTypedOrNull(VariableDeclaration::class.java, it) != null
    }
    return selfRead + descendants
}

fun EObject.variableReads(evaluator: MetaCompileTimeExpressionEvaluator): Map<VariableDeclaration, List<Expression>> {
    return variableReadExpressions(evaluator).groupBy {
        evaluator.evaluateTyped(VariableDeclaration::class.java, it)
    }
}

fun EObject.variableAssignments(evaluator: MetaCompileTimeExpressionEvaluator): Map<VariableDeclaration, List<AssignmentOperation>> {
    return eAllOfType<AssignmentOperation>().groupBy {
        evaluator.evaluateTyped(VariableDeclaration::class.java, it.reference)
    }
}

fun EObject.variableHavocs(evaluator: MetaCompileTimeExpressionEvaluator): Map<VariableDeclaration, List<HavocOperation>> {
    return eAllOfType<HavocOperation>().groupBy {
        evaluator.evaluateTyped(VariableDeclaration::class.java, it.reference)
    }
}

fun EObject.variableWrites(evaluator: MetaCompileTimeExpressionEvaluator): Map<VariableDeclaration, List<Operation>> {
    val writes = eAllOfType<AssignmentOperation>().map {
        it as Operation
    } + eAllOfType<HavocOperation>().map {
        it as Operation
    }

    return writes.groupBy {
        evaluator.evaluateTyped(VariableDeclaration::class.java, it.writeReference())
    }
}
