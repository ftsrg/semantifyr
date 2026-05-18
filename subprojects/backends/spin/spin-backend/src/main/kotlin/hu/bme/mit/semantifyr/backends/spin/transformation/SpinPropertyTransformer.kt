/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration

const val SPIN_STABLE_FLAG = "__semantifyr_stable"

data class SpinProperty(
    val ltl: String,
    val invertVerdict: Boolean,
)

class SpinPropertyTransformer @Inject constructor(
    private val spinExpressionTransformer: SpinExpressionTransformer,
) {

    fun transform(property: PropertyDeclaration): SpinProperty {
        val expression = property.expression ?: error("Property has no expression")
        return when (expression) {
            is AG -> {
                val body = spinExpressionTransformer.transform(expression.body)
                SpinProperty("[] (!$SPIN_STABLE_FLAG || ($body))", false)
            }
            is EF -> {
                val body = spinExpressionTransformer.transform(expression.body)
                SpinProperty("[] !($SPIN_STABLE_FLAG && ($body))", true)
            }
            else -> error("Unsupported property: expected AG or EF, got ${expression::class.simpleName}")
        }
    }
}
