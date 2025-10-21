/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureKind;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts;
import org.eclipse.xtext.util.IResourceScopeCache;
import org.eclipse.xtext.util.Tuples;

@Singleton
public class SubsetHandler {
    private static final String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.semantics.SuperSetHandler.CACHE_KEY";

    @Inject
    private IResourceScopeCache cache;

    @Inject
    protected BuiltinSymbolResolver builtinSymbolResolver;

    public FeatureDeclaration getSubsetFeature(FeatureDeclaration feature) {
        return cache.get(Tuples.create(CACHE_KEY, feature), feature.eResource(), () -> computeSubsetFeature(feature));
    }

    protected FeatureDeclaration computeSubsetFeature(FeatureDeclaration feature) {
        var superSet = feature.getSuperset();

        if (superSet == null && feature.getKind() == FeatureKind.CONTAINMENT) {
            if (feature.eContainer() instanceof InlinedOxsts) {
                return null;
            }

//            if (builtinSymbolResolver.isAnythingChildrenFeature(feature)) {
//                return null;
//            }
//
//            return builtinSymbolResolver.anythingChildrenFeature(feature);
        }

        if (superSet != null && superSet.eIsProxy()) {
            throw new IllegalStateException("Subset feature could not be resolved!");
        }

        return superSet;
    }

}
