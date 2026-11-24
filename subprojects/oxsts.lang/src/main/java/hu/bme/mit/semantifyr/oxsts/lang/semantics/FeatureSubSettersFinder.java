/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.domain.DomainMemberCollection;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.domain.DomainMemberCollectionProvider;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OnResourceSetChangeEvictingCache;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Declaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.xtext.util.Tuples;

public class FeatureSubSettersFinder {
    private static final String CACHE_KEY =
            "hu.bme.mit.semantifyr.oxsts.lang.semantics.FeatureSubSettersFinder.CACHE_KEY";

    @Inject
    private OnResourceSetChangeEvictingCache resourceScopeCache;

    @Inject
    private DomainMemberCollectionProvider domainMemberCollectionProvider;

    @Inject
    private RedefinitionHandler redefinitionHandler;

    public Collection<FeatureDeclaration> getSubSetters(DomainDeclaration domain, FeatureDeclaration feature) {
        return resourceScopeCache.get(
                Tuples.create(CACHE_KEY, domain, feature),
                feature.eResource(),
                () -> computeSubSetters(domain, feature));
    }

    public Collection<FeatureDeclaration> computeSubSetters(DomainDeclaration domain, FeatureDeclaration feature) {
        var memberCollection = domainMemberCollectionProvider.getMemberCollection(domain);
        var target = resolve(memberCollection, feature);

        // Preserve declaration order via LinkedHashSet. HashSet here would
        // make downstream IR shape depend on hash order, reordering dispatch
        // chains non-deterministically across runs.
        return memberCollection.getDeclarations().stream()
                .filter(d -> d instanceof FeatureDeclaration)
                .map(d -> (FeatureDeclaration) d)
                .filter(f ->
                        getSupersets(f).stream().anyMatch(superset -> resolve(memberCollection, superset) == target))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Declaration resolve(DomainMemberCollection memberCollection, FeatureDeclaration feature) {
        var resolved = memberCollection.resolveElementOrNull(feature);

        return resolved != null ? resolved : feature;
    }

    // a redefinition has the values of the feature it redefines, so it is under that feature's supersets too
    private List<FeatureDeclaration> getSupersets(FeatureDeclaration feature) {
        var supersets = new ArrayList<FeatureDeclaration>();
        var superset = feature.getSuperset();

        if (superset != null && !superset.eIsProxy()) {
            supersets.add(superset);
        }

        if (redefinitionHandler.getRedefinedDeclaration(feature) instanceof FeatureDeclaration redefined) {
            supersets.addAll(getSupersets(redefined));
        }

        return supersets;
    }
}
