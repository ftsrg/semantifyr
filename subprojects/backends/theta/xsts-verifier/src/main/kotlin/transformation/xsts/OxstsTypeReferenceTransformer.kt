/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.transformation.xsts

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DataTypeDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Multiplicity
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped

@CompilationScoped
class OxstsTypeReferenceTransformer {

    @Inject
    private lateinit var oxstsDomainTransformer: OxstsDomainTransformer

    fun transform(domainDeclaration: DomainDeclaration): XstsType {
        return transform(domainDeclaration, null)
    }

    fun transform(domainDeclaration: DomainDeclaration, multplicity: Multiplicity?): XstsType {
        val xstsType = when (domainDeclaration) {
            is EnumDeclaration -> {
                XstsFactory.createEnumType().also {
                    it.enumDeclaration = oxstsDomainTransformer.transform(domainDeclaration)
                }
            }
            is DataTypeDeclaration -> oxstsDomainTransformer.transform(domainDeclaration)
            else -> error("Unexpected variable type: $this")
        }

        if (multplicity == null) {
            return xstsType
        }

        return XstsFactory.createArrayType().also {
            it.indexType = XstsFactory.createIntegerType()
            it.elementType = xstsType
        }
    }

}
