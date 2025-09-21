/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.expression

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.domain.DomainMemberCalculator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Declaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsPackage
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RedefinableDeclaration
import org.eclipse.xtext.naming.QualifiedName

@Singleton
class RedefinitionAwareReferenceResolver {

    @Inject
    private lateinit var domainMemberCalculator: DomainMemberCalculator

    fun resolve(instance: Instance, name: String): NamedElement {
        return resolve(instance, QualifiedName.create(name))
    }

    fun resolve(instance: Instance, name: QualifiedName): NamedElement {
        val domain = domainMemberCalculator.getMembers(instance.feature)
        val elements = domain.getExportedObjects(OxstsPackage.eINSTANCE.namedElement, name, false)

        for (element in elements) {
            return element.eObjectOrProxy as NamedElement
        }

        throw IllegalArgumentException("Could not find any element named $name!")
    }

    fun resolve(instance: Instance, reference: NamedElement): NamedElement {
        if (reference is RedefinableDeclaration) {
            return resolve(instance, reference)
        }

        return reference
    }

    fun resolve(instance: Instance, redefinableDeclaration: RedefinableDeclaration): Declaration {
        val domain = domainMemberCalculator.getMemberCollection(instance.feature)

        return domain.resolveElement(redefinableDeclaration) as Declaration
    }

}
