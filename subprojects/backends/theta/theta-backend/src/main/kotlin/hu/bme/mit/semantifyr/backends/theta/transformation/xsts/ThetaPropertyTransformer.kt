/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.transformation.xsts

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.xsts.lang.xsts.Property

private typealias XstsProperty = Property

class ThetaPropertyTransformer @Inject constructor(
    private val thetaExpressionTransformer: ThetaExpressionTransformer,
) {

    fun transform(propertyDeclaration: PropertyDeclaration): XstsProperty {
        return XstsFactory.createProperty().also {
            it.invariant = thetaExpressionTransformer.transform(propertyDeclaration.expression)
        }
    }
}
