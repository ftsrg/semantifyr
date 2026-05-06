/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.discovery

import com.google.inject.Inject
import hu.bme.mit.semantifyr.frontends.gamma.GammaVerificationCase
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VerificationCaseDeclaration
import org.eclipse.xtext.naming.IQualifiedNameConverter
import org.eclipse.xtext.naming.IQualifiedNameProvider

class GammaVerificationCaseDiscoverer @Inject constructor(
    private val qualifiedNameProvider: IQualifiedNameProvider,
    private val qualifiedNameConverter: IQualifiedNameConverter,
) {

    fun discover(gammaModel: GammaModelPackage): List<GammaVerificationCase> {
        return gammaModel.verificationCases.map {
            mapDeclaration(it)
        }
    }

    fun mapDeclaration(declaration: VerificationCaseDeclaration): GammaVerificationCase {
        return GammaVerificationCase(qualifiedNameOf(declaration), declaration)
    }

    private fun qualifiedNameOf(declaration: VerificationCaseDeclaration): String {
        val qualifiedName = qualifiedNameProvider.getFullyQualifiedName(declaration) ?: error("Cannot compute qualified name for verification case '${declaration.name}'")
        val name = qualifiedNameConverter.toString(qualifiedName)
        return name
    }

}
