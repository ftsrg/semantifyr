/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import hu.bme.mit.semantifyr.backend.witness.WitnessState
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger

fun WitnessState.valuesByName(): Map<String, Any> {
    return values.associate {
        val value = when (val expression = it.value) {
            is LiteralInteger -> expression.value
            is LiteralBoolean -> expression.isValue
            is ElementReference -> (expression.element as EnumLiteral).name
            else -> error("Unexpected witness value $expression")
        }
        it.variable.name to value
    }
}
