/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.domain.DomainMemberCollectionProvider;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OnResourceSetChangeEvictingCache;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RedefinableDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.util.Strings;
import org.eclipse.xtext.util.Tuples;

public class RedefinitionHandler {
    private static final String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler.CACHE_KEY";

    @Inject
    private OnResourceSetChangeEvictingCache cache;

    @Inject
    protected DomainMemberCollectionProvider domainMemberCollectionProvider;

    public RedefinableDeclaration getRedefinedDeclaration(RedefinableDeclaration declaration) {
        return cache.get(
                Tuples.create(CACHE_KEY, declaration),
                declaration.eResource(),
                () -> computeRedefinedDeclaration(declaration));
    }

    protected RedefinableDeclaration computeRedefinedDeclaration(RedefinableDeclaration declaration) {
        var redefined = declaration.getRedefined();

        if (redefined != null) {
            if (redefined.eIsProxy()) {
                return null;
            }

            return redefined;
        }

        var name = NamingUtil.getName(declaration);
        if ((declaration.isRedefine() || isAnonymousTransition(declaration)) && name != null) {
            // find the other RedefinableDeclaration in the parent domain with the same name
            var surroundingFeature = (DomainDeclaration) declaration.eContainer();
            var parentDomain = domainMemberCollectionProvider.getParentCollection(surroundingFeature);
            var found = parentDomain
                    .getMemberSelectable()
                    .getExportedObjects(declaration.eClass(), QualifiedName.create(name), false);

            var iterator = found.iterator();
            if (iterator.hasNext()) {
                var toReturn = (RedefinableDeclaration) iterator.next().getEObjectOrProxy();
                if (toReturn.eIsProxy()) {
                    return null;
                }

                return toReturn;
            }
        }

        return null;
    }

    // a class has one main and one init transition, so an anonymous one is always the class's own
    protected boolean isAnonymousTransition(RedefinableDeclaration declaration) {
        return declaration instanceof TransitionDeclaration && Strings.isEmpty(declaration.getName());
    }
}
