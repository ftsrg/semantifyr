/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class NuxmvExpressionTransformer @Inject constructor(
    private val nuxmvExpressionVisitorFactory: NuxmvExpressionVisitor.Factory,
) {

    fun transform(
        expression: Expression,
        substitution: Map<VariableDeclaration, String> = emptyMap(),
    ): String {
        return nuxmvExpressionVisitorFactory.create(substitution).transform(expression)
    }
}
