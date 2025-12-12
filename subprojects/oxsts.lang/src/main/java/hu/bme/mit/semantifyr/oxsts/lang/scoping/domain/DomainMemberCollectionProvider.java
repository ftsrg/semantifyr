/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.scoping.domain;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.InheritanceHandler;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OnResourceSetChangeEvictingCache;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;
import org.eclipse.xtext.resource.ISelectable;
import org.eclipse.xtext.util.Tuples;

@Singleton
public class DomainMemberCollectionProvider {
    private final static String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.typesystem.domain.DomainMemberCollector.CACHE_KEY";
    private final static String MEMBER_KEY = CACHE_KEY + "MEMBER";
    private final static String PARENT_KEY = CACHE_KEY + "PARENT";

    @Inject
    private InheritanceHandler inheritanceHandler;

    @Inject
    private RedefinitionHandler redefinitionHandler;

    @Inject
    private OnResourceSetChangeEvictingCache cache;

    public ISelectable getMembers(DomainDeclaration domain) {
        var collection = getMemberCollection(domain);

        return collection.getMemberSelectable();
    }

    public DomainMemberCollection getParentCollection(DomainDeclaration domain) {
        return cache.get(Tuples.create(PARENT_KEY, domain), domain.eResource(), () -> computeParentCollection(domain));
    }

    protected DomainMemberCollection computeParentCollection(DomainDeclaration domain) {
        var superDomains = inheritanceHandler.getSuperDomains(domain).stream().map(this::getMemberCollection).toList();

        return DomainMemberCollection.createCollection(superDomains, redefinitionHandler);
    }

    public DomainMemberCollection getMemberCollection(DomainDeclaration domain) {
        return cache.get(Tuples.create(MEMBER_KEY, domain), domain.eResource(), () -> computeMemberCollection(domain));
    }

    protected DomainMemberCollection computeMemberCollection(DomainDeclaration domain) {
        var parent = getParentCollection(domain);

        return DomainMemberCollection.createCollection(domain, parent, redefinitionHandler);
    }

}
