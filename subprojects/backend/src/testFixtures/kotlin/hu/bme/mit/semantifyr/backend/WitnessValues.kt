/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import hu.bme.mit.semantifyr.backend.witness.WitnessState
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger

fun WitnessState.integer(name: String): Int {
    val expression = valueOf(name)
    if (expression !is LiteralInteger) {
        error("The witness value of $name is not an integer: $expression")
    }
    return expression.value
}

fun WitnessState.boolean(name: String): Boolean {
    val expression = valueOf(name)
    if (expression !is LiteralBoolean) {
        error("The witness value of $name is not a boolean: $expression")
    }
    return expression.isValue
}

fun WitnessState.enumeration(name: String): String {
    val literal = (valueOf(name) as? ElementReference)?.element
    if (literal !is EnumLiteral) {
        error("The witness value of $name is not an enum literal: $literal")
    }
    return literal.name
}

private fun WitnessState.valueOf(name: String): Expression {
    val stateValue = values.firstOrNull {
        it.variable.name == name
    } ?: error("The witness state has no variable $name")
    return stateValue.value
}
