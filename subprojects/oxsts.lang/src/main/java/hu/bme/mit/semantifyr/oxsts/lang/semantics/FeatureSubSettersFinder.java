/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.domain.DomainMemberCollectionProvider;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OnResourceSetChangeEvictingCache;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration;
import org.eclipse.xtext.util.Tuples;

import java.util.Collection;
import java.util.stream.Collectors;

public class FeatureSubSettersFinder {
    private static final String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.semantics.FeatureSubSettersFinder.CACHE_KEY";

    @Inject
    private OnResourceSetChangeEvictingCache resourceScopeCache;

    @Inject
    private DomainMemberCollectionProvider domainMemberCollectionProvider;

    @Inject
    private SubsetHandler subsetHandler;

    public Collection<FeatureDeclaration> getSubSetters(DomainDeclaration domain, FeatureDeclaration feature) {
        return resourceScopeCache.get(Tuples.create(CACHE_KEY, domain, feature), feature.eResource(), () -> computeSubSetters(domain, feature));
    }

    public Collection<FeatureDeclaration>  computeSubSetters(DomainDeclaration domain, FeatureDeclaration feature) {
        var memberCollection = domainMemberCollectionProvider.getMemberCollection(domain);

        return memberCollection.getDeclarations().stream()
                .filter(d -> d instanceof FeatureDeclaration)
                .map(d -> (FeatureDeclaration)d)
                .filter(f -> subsetHandler.getSubsetFeature(f) == feature)
                .collect(Collectors.toSet());
    }

}
