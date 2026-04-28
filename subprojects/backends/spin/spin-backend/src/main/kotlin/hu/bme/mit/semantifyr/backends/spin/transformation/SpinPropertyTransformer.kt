/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration

data class SpinProperty(
    val ltl: String,
    val invertVerdict: Boolean,
)

@VerificationScoped
class SpinPropertyTransformer {
    @Inject
    private lateinit var spinExpressionTransformer: SpinExpressionTransformer

    fun transform(property: PropertyDeclaration): SpinProperty {
        val expression = property.expression ?: error("Property has no expression")
        return when (expression) {
            is AG -> {
                val body = spinExpressionTransformer.transform(expression.body)
                SpinProperty(ltl = "[] (!$STABLE_FLAG || ($body))", invertVerdict = false)
            }
            is EF -> {
                val body = spinExpressionTransformer.transform(expression.body)
                SpinProperty(ltl = "[] !($STABLE_FLAG && ($body))", invertVerdict = true)
            }
            else -> {
                error("Unsupported property: expected AG or EF, got ${expression::class.simpleName}")
            }
        }
    }

    companion object {
        const val STABLE_FLAG: String = "__semantifyr_stable"
    }
}
