/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.domain.DomainMemberCalculator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.util.IResourceScopeCache;
import org.eclipse.xtext.util.Tuples;

@Singleton
public class RedefinitionHandler {
    private static final String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler.CACHE_KEY";

    @Inject
    private IResourceScopeCache cache;

    @Inject
    protected BuiltinSymbolResolver builtinSymbolResolver;

    @Inject
    protected DomainMemberCalculator domainMemberCalculator;

    @Inject
    protected OxstsQualifiedNameProvider qualifiedNameProvider;

    public RedefinableDeclaration getRedefinedDeclaration(RedefinableDeclaration declaration) {
        return cache.get(Tuples.create(CACHE_KEY, declaration), declaration.eResource(), () -> computeRedefinedDeclaration(declaration));
    }

    protected RedefinableDeclaration computeRedefinedDeclaration(RedefinableDeclaration declaration) {
        var redefined = declaration.getRedefined();

        if (redefined != null) {
            return redefined;
        }

        if (declaration.isRedefine()) {
            // find the other RedefinableDeclaration in the parent domain with the same name

            var parentDomain = domainMemberCalculator.getParentCollection(declaration);
            var found = parentDomain.getMembers().getExportedObjects(declaration.eClass(), QualifiedName.create(NamingUtil.getName(declaration)), false);

            for (var element : found) {
                return (RedefinableDeclaration) element.getEObjectOrProxy();
            }

//            if (declaration instanceof FeatureDeclaration featureDeclaration) {
//
//                if (builtinSymbolResolver.isAnythingParentFeature(featureDeclaration)) {
//                    return null;
//                }
//
//                return builtinSymbolResolver.anythingParentFeature(declaration);
//            }
        }

        return null;
    }

}
