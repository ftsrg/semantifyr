/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.transformation.xsts

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DataTypeDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Multiplicity

class ThetaTypeReferenceTransformer @Inject constructor(
    private val thetaDomainTransformer: ThetaDomainTransformer,
) {

    fun transform(domainDeclaration: DomainDeclaration, multiplicity: Multiplicity?): XstsType {
        val xstsType = when (domainDeclaration) {
            is EnumDeclaration -> XstsFactory.createEnumType().also {
                it.enumDeclaration = thetaDomainTransformer.transform(domainDeclaration)
            }
            is DataTypeDeclaration -> thetaDomainTransformer.transform(domainDeclaration)
            else -> error("Unexpected variable type: $domainDeclaration")
        }

        if (multiplicity == null) {
            return xstsType
        }

        return XstsFactory.createArrayType().also {
            it.indexType = XstsFactory.createIntegerType()
            it.elementType = xstsType
        }
    }
}
