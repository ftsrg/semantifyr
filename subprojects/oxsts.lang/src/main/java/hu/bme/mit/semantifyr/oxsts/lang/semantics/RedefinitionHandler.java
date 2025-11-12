/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.domain.DomainMemberCollectionProvider;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RedefinableDeclaration;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.util.IResourceScopeCache;
import org.eclipse.xtext.util.Tuples;

@Singleton
public class RedefinitionHandler {
    private static final String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler.CACHE_KEY";

    @Inject
    private IResourceScopeCache cache;

    @Inject
    protected DomainMemberCollectionProvider domainMemberCollectionProvider;

    public RedefinableDeclaration getRedefinedDeclaration(RedefinableDeclaration declaration) {
        return cache.get(Tuples.create(CACHE_KEY, declaration), declaration.eResource(), () -> computeRedefinedDeclaration(declaration));
    }

    public RedefinableDeclaration getRedefinerDeclarations(RedefinableDeclaration declaration) {
        return cache.get(Tuples.create(CACHE_KEY, declaration), declaration.eResource(), () -> computeRedefinerDeclarations(declaration));
    }

    protected RedefinableDeclaration computeRedefinedDeclaration(RedefinableDeclaration declaration) {
        var redefined = declaration.getRedefined();

        if (redefined != null) {
            if (redefined.eIsProxy()) {
                return null;
            }

            return redefined;
        }

        if (declaration.isRedefine()) {
            // find the other RedefinableDeclaration in the parent domain with the same name
            var surroundingFeature = (DomainDeclaration) declaration.eContainer();
            var parentDomain = domainMemberCollectionProvider.getParentCollection(surroundingFeature);
            var found = parentDomain.getMemberSelectable().getExportedObjects(declaration.eClass(), QualifiedName.create(NamingUtil.getName(declaration)), false);

            for (var element : found) {
                var toReturn = (RedefinableDeclaration) element.getEObjectOrProxy();
                if (toReturn.eIsProxy()) {
                    return null;
                }

                return toReturn;
            }
        }

        return null;
    }

    protected RedefinableDeclaration computeRedefinerDeclarations(RedefinableDeclaration declaration) {
        var redefined = declaration.getRedefined();

        if (redefined != null) {
            if (redefined.eIsProxy()) {
                return null;
            }

            return redefined;
        }

        if (declaration.isRedefine()) {
            // find the other RedefinableDeclaration in the parent domain with the same name
            var surroundingFeature = (DomainDeclaration) declaration.eContainer();
            var parentDomain = domainMemberCollectionProvider.getParentCollection(surroundingFeature);
            var found = parentDomain.getMemberSelectable().getExportedObjects(declaration.eClass(), QualifiedName.create(NamingUtil.getName(declaration)), false);

            for (var element : found) {
                var toReturn = (RedefinableDeclaration) element.getEObjectOrProxy();
                if (toReturn.eIsProxy()) {
                    return null;
                }

                return toReturn;
            }
        }

        return null;
    }

}
