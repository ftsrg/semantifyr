/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Declaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureKind;
import org.eclipse.xtext.util.IResourceScopeCache;
import org.eclipse.xtext.util.Tuples;

@Singleton
public class RedefinitionHandler {
    private static final String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler.CACHE_KEY";

    @Inject
    private IResourceScopeCache cache;

    @Inject
    protected BuiltinSymbolResolver builtinSymbolResolver;

    public DomainDeclaration getRedefinedDeclaration(DomainDeclaration declaration) {
        return getRedefinedDeclaration((Declaration) declaration);
    }

    public DomainDeclaration getRedefinedDeclaration(Declaration declaration) {
        return cache.get(Tuples.create(CACHE_KEY, declaration), declaration.eResource(), () -> computeRedefinedDeclaration(declaration));
    }

    protected DomainDeclaration computeRedefinedDeclaration(Declaration declaration) {
        if (declaration instanceof FeatureDeclaration featureDeclaration) {
            var redefined = featureDeclaration.getRedefined();

            if (redefined == null && featureDeclaration.getKind() == FeatureKind.CONTAINER) {
                if (builtinSymbolResolver.isAnythingParentFeature(featureDeclaration)) {
                    return null;
                }

                return builtinSymbolResolver.anythingParentFeature(declaration);
            }

            return redefined;
        }

        return null;
    }

}
