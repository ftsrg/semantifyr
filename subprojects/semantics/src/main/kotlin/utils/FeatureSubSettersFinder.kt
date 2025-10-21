/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.utils

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.lang.scoping.domain.DomainMemberCollectionProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.SubsetHandler
import hu.bme.mit.semantifyr.oxsts.lang.utils.OnResourceSetChangeEvictingCache
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import org.eclipse.xtext.util.Tuples

@Singleton
class FeatureSubSettersFinder {

    private val CACHE_KEY: String = "${javaClass.canonicalName}.CACHE_KEY"

    @Inject
    private lateinit var resourceScopeCache: OnResourceSetChangeEvictingCache

    @Inject
    private lateinit var domainMemberCollectionProvider: DomainMemberCollectionProvider

    @Inject
    private lateinit var subsetHandler: SubsetHandler

    fun getSubSetters(domain: DomainDeclaration, feature: FeatureDeclaration): Collection<FeatureDeclaration> {
        return resourceScopeCache.get(Tuples.create(CACHE_KEY, domain, feature), feature.eResource()) {
            computeSubSetters(domain, feature)
        }
    }

    fun computeSubSetters(domain: DomainDeclaration, feature: FeatureDeclaration): Collection<FeatureDeclaration> {
        val memberCollection = domainMemberCollectionProvider.getMemberCollection(domain)

        return memberCollection.declarations.filterIsInstance<FeatureDeclaration>().filter {
            subsetHandler.getSubsetFeature(it) == feature
        }.distinct()
    }

}
