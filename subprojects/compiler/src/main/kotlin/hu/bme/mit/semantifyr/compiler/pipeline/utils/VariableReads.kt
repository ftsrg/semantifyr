/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.utils

import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaCompileTimeExpressionEvaluator
import hu.bme.mit.semantifyr.compiler.pipeline.expression.tryEvaluateTypedOrNull
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
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
