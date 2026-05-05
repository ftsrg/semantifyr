/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration

data class NuxmvProperty(
    val invariant: String,
    val invertVerdict: Boolean,
)

class NuxmvPropertyTransformer @Inject constructor(
    private val nuxmvExpressionTransformer: NuxmvExpressionTransformer,
) {

    fun transform(property: PropertyDeclaration): NuxmvProperty {
        val expression = property.expression ?: error("Property has no expression")
        return when (expression) {
            is AG -> NuxmvProperty(nuxmvExpressionTransformer.transform(expression.body), false)
            is EF -> NuxmvProperty("!(${nuxmvExpressionTransformer.transform(expression.body)})", true)
            else -> error("Unsupported property: expected AG or EF, got ${expression::class.simpleName}")
        }
    }
}
