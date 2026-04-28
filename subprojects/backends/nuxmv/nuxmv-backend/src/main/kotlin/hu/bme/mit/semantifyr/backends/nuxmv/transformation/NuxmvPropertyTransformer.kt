/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration

data class NuxmvProperty(
    val invariant: String,
    val invertVerdict: Boolean,
)

@VerificationScoped
class NuxmvPropertyTransformer {
    @Inject
    private lateinit var nuxmvExpressionTransformer: NuxmvExpressionTransformer

    fun transform(property: PropertyDeclaration): NuxmvProperty {
        val expression = property.expression ?: error("Property has no expression")
        return when (expression) {
            is AG -> NuxmvProperty(invariant = nuxmvExpressionTransformer.transform(expression.body), invertVerdict = false)
            is EF -> NuxmvProperty(invariant = "!(${nuxmvExpressionTransformer.transform(expression.body)})", invertVerdict = true)
            else -> error("Unsupported property: expected AG or EF, got ${expression::class.simpleName}")
        }
    }
}
