/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration

private const val STABLE_QUALIFIED =
    "${UppaalModelTransformer.TEMPLATE_NAME}.${UppaalModelTransformer.STABLE_LOCATION_NAME}"

class UppaalPropertyTransformer @Inject constructor(
    private val uppaalExpressionTransformer: UppaalExpressionTransformer,
) {

    fun transform(property: PropertyDeclaration): String {
        val expression = property.expression
            ?: error("Property has no expression")
        return when (expression) {
            is AG -> {
                val body = uppaalExpressionTransformer.transform(expression.body)
                "A[] ($STABLE_QUALIFIED imply ($body))"
            }
            is EF -> {
                val body = uppaalExpressionTransformer.transform(expression.body)
                "E<> ($STABLE_QUALIFIED and ($body))"
            }
            else -> error("Unsupported property: expected AG or EF, got ${expression::class.simpleName}")
        }
    }
}
