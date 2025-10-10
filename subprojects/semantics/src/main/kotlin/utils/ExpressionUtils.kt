/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.utils

import hu.bme.mit.semantifyr.oxsts.model.oxsts.AbstractForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

fun isReferenceContextual(elementReference: ElementReference): Boolean {
    val element = elementReference.element
    val container = element.eContainer()

    if (container is AbstractForOperation && container.loopVariable == element) {
        return false
    }

    return element is FeatureDeclaration
            || (element is VariableDeclaration && element !is LocalVarDeclarationOperation)
            || element is PropertyDeclaration
            || element is TransitionDeclaration
}
