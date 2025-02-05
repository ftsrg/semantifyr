/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.resolution

import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChainingExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DeclarationReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Property
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Type
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.type

class PropertyResolver(
    private val instance: Instance
) {

    // TODO: this is a name-based redefinition handler, we should support classic redefinition
    //  to do that, we need to add syntax support as well!
    fun resolveProperty(expression: ChainingExpression): Property {
        val type = instance.type

        return type.findPropertyUpwards(expression)
    }

    private fun Type.findPropertyUpwards(expression: ChainingExpression): Property {
        val property = getProperty(expression) ?: supertype?.findPropertyUpwards(expression)

        check(property != null) {
            "Property $expression could not be found in the type hierarchy!"
        }

        return property
    }

    private fun Type.getProperty(expression: ChainingExpression): Property? {
        return when (expression) {
            is DeclarationReferenceExpression -> {
                val reference = expression.element as Property

                properties.firstOrNull {
                    it.name == reference.name
                }
            }

            else -> error("Unknown expression: $expression")
        }
    }

}
