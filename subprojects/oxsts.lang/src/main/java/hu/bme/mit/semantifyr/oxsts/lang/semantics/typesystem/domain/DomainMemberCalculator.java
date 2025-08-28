/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.domain;

import com.google.common.collect.FluentIterable;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.InheritanceHandler;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OnResourceSetChangeEvictingCache;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Declaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.resource.ISelectable;
import org.eclipse.xtext.util.Tuples;

@Singleton
public class DomainMemberCalculator {
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

        return collection.getMembers();
    }

    public DomainMemberCollection getMemberCollection(DomainDeclaration domain) {
        return cache.get(Tuples.create(MEMBER_KEY, domain), domain.eResource(), () -> computeMemberCollection(domain));
    }

    public DomainMemberCollection getParentCollection(Declaration domain) {
        return cache.get(Tuples.create(PARENT_KEY, domain), domain.eResource(), () -> computeParentCollection(domain));
    }

    protected DomainMemberCollection computeMemberCollection(DomainDeclaration domain) {
        var parent = getParentCollection(domain);

        return new DomainMemberCollection(domain, parent, redefinitionHandler);
    }

    protected DomainMemberCollection computeParentCollection(Declaration declaration) {
        if (declaration instanceof DomainDeclaration domainDeclaration) {
            return FluentIterable.from(inheritanceHandler.getSuperDomains(domainDeclaration)).transform(this::getMemberCollection).stream()
                    .reduce(DomainMemberCollection.EMPTY, DomainMemberCollection::merge);
        }

        var parentDomain = EcoreUtil2.getContainerOfType(declaration, DomainDeclaration.class);
        return getParentCollection(parentDomain);
    }

}
