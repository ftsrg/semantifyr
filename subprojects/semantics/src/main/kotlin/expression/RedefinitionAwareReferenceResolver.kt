/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.expression

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.scoping.domain.DomainMemberCollectionProvider
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Declaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsPackage
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RedefinableDeclaration
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import org.eclipse.xtext.naming.QualifiedName

@CompilationScoped
class RedefinitionAwareReferenceResolver {

    @Inject
    private lateinit var domainMemberCollectionProvider: DomainMemberCollectionProvider

    fun resolve(domain: DomainDeclaration, name: String): NamedElement {
        return resolveOrNull(domain, name) ?: throw IllegalArgumentException("Could not find any element named $name!")
    }

    fun resolveOrNull(domain: DomainDeclaration, name: String): NamedElement? {
        return resolveOrNull(domain, QualifiedName.create(name))
    }

    fun resolve(domain: DomainDeclaration, name: QualifiedName): NamedElement {
        return resolveOrNull(domain, name) ?: throw IllegalArgumentException("Could not find any element named $name!")
    }

    fun resolveOrNull(domain: DomainDeclaration, name: QualifiedName): NamedElement? {
        val domain = domainMemberCollectionProvider.getMembers(domain)
        val elements = domain.getExportedObjects(OxstsPackage.eINSTANCE.namedElement, name, false)

        for (element in elements) {
            return element.eObjectOrProxy as NamedElement
        }

        return null
    }

    fun resolve(domain: DomainDeclaration, reference: NamedElement): NamedElement {
        return resolveOrNull(domain, reference) ?: throw IllegalArgumentException("Could not resolve element ${reference.name}!")
    }

    fun resolveOrNull(domain: DomainDeclaration, reference: NamedElement): NamedElement? {
        if (reference is RedefinableDeclaration && OxstsUtils.isElementRedefinable(reference)) {
            return resolveOrNull(domain, reference)
        }

        return reference
    }

    fun resolve(domain: DomainDeclaration, redefinableDeclaration: RedefinableDeclaration): Declaration {
        return resolveOrNull(domain, redefinableDeclaration) ?: throw IllegalArgumentException("Could not resolve element ${redefinableDeclaration.name}!")
    }

    fun resolveOrNull(domain: DomainDeclaration, redefinableDeclaration: RedefinableDeclaration): Declaration? {
        val memberCollection = domainMemberCollectionProvider.getMemberCollection(domain)

        return memberCollection.resolveElementOrNull(redefinableDeclaration)
    }

}
