/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OnResourceSetChangeEvictingCache;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration;
import org.eclipse.xtext.util.IResourceScopeCache;
import org.eclipse.xtext.util.Tuples;

@Singleton
public class OppositeHandler {

    private static final String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.semantics.OppositeHandler.CACHE_KEY";

    @Inject
    private OnResourceSetChangeEvictingCache cache;

    @Inject
    private RedefinitionHandler redefinitionHandler;

    @Inject
    private SubsetHandler subsetHandler;

    public FeatureDeclaration getOppositeFeature(FeatureDeclaration feature) {
        return cache.get(Tuples.create(CACHE_KEY, feature), feature.eResource(), () -> computeOppositeFeature(feature));
    }

    protected FeatureDeclaration computeOppositeFeature(FeatureDeclaration feature) {
        var opposite = feature.getOpposite();

        if (opposite != null) {
            if (opposite.eIsProxy()) {
                return null;
            }
            return opposite;
        }

        var redefined = redefinitionHandler.getRedefinedDeclaration(feature);

        if (redefined instanceof FeatureDeclaration redefinedFeature) {
            return getOppositeFeature(redefinedFeature);
        }

        var subsetFeature = subsetHandler.getSubsetFeature(feature);

        if (subsetFeature == null) {
            return null;
        }

        return getOppositeFeature(subsetFeature);
    }

}
