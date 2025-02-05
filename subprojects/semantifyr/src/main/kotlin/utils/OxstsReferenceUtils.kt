/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.utils

import hu.bme.mit.semantifyr.oxsts.model.oxsts.DeclarationReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ReferenceExpression
import kotlin.streams.asSequence

fun Element.resolveElement(name: String): DeclarationReferenceExpression {
    val accessibleElements = OxstsUtils.getAccessibleElements(this)

    val element = accessibleElements.asSequence().filterIsInstance<Element>().first {
        it.name == name
    }

    return OxstsFactory.createDeclarationReferenceExpression(element)
}

fun Element.resolvePath(path: String): ReferenceExpression {
    val segments = path.split(Namings.SYNTHETIC_SEPARATOR).drop(2) // first is 'this'
    val chains = mutableListOf<DeclarationReferenceExpression>()
    var lastElement = this

    for (segment in segments) {
        chains += lastElement.resolveElement(segment)
        lastElement = chains.last().element
    }

    return OxstsFactory.createChainReferenceExpression(chains)
}
