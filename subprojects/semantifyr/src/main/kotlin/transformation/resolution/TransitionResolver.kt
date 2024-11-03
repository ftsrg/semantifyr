/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.resolution

import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.type
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChainingExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DeclarationReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Transition
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Type
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.isHavocTransition
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.isInitTransition
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.isMainTransition

class TransitionResolver(
    private val instance: Instance
) {

    // TODO: this is a name-based redefinition handler, we should support classic redefinition
    //  to do that, we need to add syntax support as well!
    fun resolveTransition(expression: ChainingExpression): Transition {
        val type = instance.type

        return type.findTransitionUpwards(expression)
    }

    private fun Type.findTransitionUpwards(expression: ChainingExpression): Transition {
        val transition = getTransition(expression) ?: supertype?.findTransitionUpwards(expression)

        check(transition != null) {
            "Transition $expression could not be found in the type hierarchy!"
        }

        return transition
    }

    private fun Type.getTransition(expression: ChainingExpression): Transition? {
        return when (expression) {
            is DeclarationReferenceExpression -> {
                val reference = expression.element as Transition

                when {
                    reference.isMainTransition -> mainTransition.firstOrNull()
                    reference.isInitTransition -> initTransition.firstOrNull()
                    reference.isHavocTransition -> havocTransition.firstOrNull()
                    else -> transitions.firstOrNull {
                        it.name == reference.name
                    }
                }
            }
            else -> error("Unknown expression: $expression")
        }
    }

}
