/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.scoping.domain;

import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Declaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RedefinableDeclaration;
import org.eclipse.xtext.resource.ISelectable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DomainMemberCollection {

    public static final DomainMemberCollection EMPTY = new DomainMemberCollection(null) {

        @Override
        public String toString() {
            return "EMPTY";
        }

    };

    public static DomainMemberCollection createCollection(DomainDeclaration domainDeclaration, RedefinitionHandler redefinitionHandler) {
        return createCollection(domainDeclaration, EMPTY, redefinitionHandler);
    }

    public static DomainMemberCollection createCollection(DomainDeclaration domainDeclaration, DomainMemberCollection parent, RedefinitionHandler redefinitionHandler) {
        return new InheritanceAwareDomainMemberCollection(domainDeclaration, parent, redefinitionHandler);
    }

    public static DomainMemberCollection createCollection(List<DomainMemberCollection> parents, RedefinitionHandler redefinitionHandler) {
        return switch (parents.size()) {
            case 0 -> EMPTY;
            case 1 -> parents.getFirst();
            default -> new MergedDomainMemberCollection(parents, redefinitionHandler);
        };
    }

    private final RedefinitionHandler redefinitionHandler;
    private final List<DeclarationHolder> declarationHolders;
    private final Map<Declaration, DeclarationHolder> redefinitions;

    private DomainMemberSelectable domainMemberSelectable;
    private List<Declaration> actualDeclarations;

    protected DomainMemberCollection(RedefinitionHandler redefinitionHandler) {
        this.redefinitionHandler = redefinitionHandler;
        this.declarationHolders = new ArrayList<>();
        this.redefinitions = new HashMap<>();
    }

    protected DomainMemberCollection(DomainMemberCollection parent, RedefinitionHandler redefinitionHandler) {
        this.redefinitionHandler = redefinitionHandler;
        this.declarationHolders = new ArrayList<>();
        this.redefinitions = new HashMap<>();

        var declarationHolderMap = new HashMap<DeclarationHolder, DeclarationHolder>();

        for (var declaration : parent.declarationHolders) {
            var clonedHolder = new DeclarationHolder(declaration.getDeclaration());
            declarationHolders.add(clonedHolder);
            declarationHolderMap.put(declaration, clonedHolder);
        }

        for (var entry : parent.redefinitions.entrySet()) {
            var decl = entry.getKey();
            var holder = entry.getValue();
            var clonedHolder = declarationHolderMap.get(holder);
            redefinitions.put(decl, clonedHolder);
        }
    }

    public Declaration resolveElement(Declaration element) {
        var resolvedElement = resolveElementOrNull(element);

        if (resolvedElement == null) {
            throw new IllegalArgumentException("This domain does not have this element!");
        }

        return resolvedElement;
    }

    public Declaration resolveElementOrNull(Declaration element) {
        var redefinedHolder = redefinitions.get(element);

        if (redefinedHolder == null) {
            return null;
        }

        return redefinedHolder.getDeclaration();
    }

    public ISelectable getMemberSelectable() {
        if (domainMemberSelectable == null) {
            domainMemberSelectable = new DomainMemberSelectable(this);
        }

        return domainMemberSelectable;
    }

    public List<Declaration> getDeclarations() {
        if (actualDeclarations == null) {
            actualDeclarations = declarationHolders.stream().map(h -> h.declaration).toList();
        }

        return actualDeclarations;
    }

    protected <T extends Declaration> void addMembers(List<T> members) {
        for (var member : members) {
            addMember(member);
        }
    }

    private <T extends Declaration> void addMember(T declaration) {
        if (declaration instanceof RedefinableDeclaration redefinableDeclaration) {
            var redefined = redefinitionHandler.getRedefinedDeclaration(redefinableDeclaration);
            if (redefined != null) {
                add(declaration, redefined);
                return;
            }
        }

        add(declaration);
    }

    protected void add(Declaration declaration) {
        var declarationHolder = new DeclarationHolder(declaration);
        declarationHolders.add(declarationHolder);
        redefinitions.put(declaration, declarationHolder);
    }

    protected void add(Declaration declaration, Declaration redefined) {
        var redefinedHolder = redefinitions.get(redefined);
        if (redefinedHolder == null) {
            throw new IllegalStateException("Redefined element can not be found in this collection!");
        }
        redefinedHolder.setDeclaration(declaration);
        redefinitions.put(declaration, redefinedHolder);
    }

}
