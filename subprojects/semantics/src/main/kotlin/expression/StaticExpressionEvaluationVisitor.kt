/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.expression

import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.utils.ExpressionEvaluationVisitor

abstract class StaticExpressionEvaluationVisitor<T> : ExpressionEvaluationVisitor<T>() {

    override fun visit(evaluation: ExpressionEvaluation): T {
        return when (evaluation) {
            is InstanceEvaluation -> visit(evaluation)
            else -> super.visit(evaluation)
        }
    }

    protected abstract fun visit(evaluation: InstanceEvaluation): T

}
