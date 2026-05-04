/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.transformation

import hu.bme.mit.semantifyr.backend.BackendUnsupportedException
import hu.bme.mit.semantifyr.oxsts.lang.utils.ExpressionVisitor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CastExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInfinity
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralNothing
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralString
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RangeExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SelfReference

abstract class BackendExpressionVisitor<T> : ExpressionVisitor<T>() {

    protected abstract val backendName: String

    override fun visit(expression: SelfReference): T {
        error("Unexpected self reference")
    }

    override fun visit(expression: NavigationSuffixExpression): T {
        error("Unexpected navigation expression")
    }

    override fun visit(expression: CallSuffixExpression): T {
        error("Unexpected call expression")
    }

    override fun visit(expression: RangeExpression): T {
        error("Unexpected range expression")
    }

    override fun visit(expression: AG): T {
        error("Unexpected AG expression")
    }

    override fun visit(expression: EF): T {
        error("Unexpected EF expression")
    }

    override fun visit(expression: CastExpression): T {
        error("Unexpected CastExpression reference")
    }

    override fun visit(expression: LiteralString): T {
        throw BackendUnsupportedException("$backendName does not support string literals")
    }

    override fun visit(expression: LiteralNothing): T {
        error("Unexpected LiteralNothing reference")
    }

    override fun visit(expression: LiteralInfinity): T {
        error("Unexpected LiteralInfinity reference")
    }

    override fun visit(expression: ArrayLiteral): T {
        throw BackendUnsupportedException("$backendName does not support array literals")
    }
}
