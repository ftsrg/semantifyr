/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.utils

import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean

val ChoiceOperation.allBranches
    get() = if (`else` == null) {
        branches
    } else {
        branches + `else`
    }

@Suppress("SimplifyBooleanWithConstants")
val Expression.isConstantLiteralFalse
    get() = this is LiteralBoolean && isValue == false

val Expression.isConstantLiteralTrue
    get() = this is LiteralBoolean && isValue
