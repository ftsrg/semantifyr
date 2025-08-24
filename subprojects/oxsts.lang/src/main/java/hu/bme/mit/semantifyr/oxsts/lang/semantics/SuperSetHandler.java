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
import org.eclipse.xtext.util.IResourceScopeCache;
import org.eclipse.xtext.util.Tuples;

import java.util.List;

@Singleton
public class SuperSetHandler {
    private static final String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.semantics.SuperSetHandler.CACHE_KEY";

    @Inject
    private IResourceScopeCache cache;

    @Inject
    protected BuiltinSymbolResolver builtinSymbolResolver;

    public Iterable<FeatureDeclaration> getSuperSetFeatures(FeatureDeclaration declaration) {
        return cache.get(Tuples.create(CACHE_KEY, declaration), declaration.eResource(), () -> computeSuperSetFeatures(declaration));
    }

    protected Iterable<FeatureDeclaration> computeSuperSetFeatures(FeatureDeclaration declaration) {
        var superSet = declaration.getSuperSets();

        if (superSet.isEmpty() && declaration.getKind() == FeatureKind.CONTAINMENT) {
            if (builtinSymbolResolver.isAnythingChildrenFeature(declaration)) {
                return List.of();
            }

            return List.of(builtinSymbolResolver.anythingChildrenFeature(declaration));
        }

        return superSet;
    }

}
