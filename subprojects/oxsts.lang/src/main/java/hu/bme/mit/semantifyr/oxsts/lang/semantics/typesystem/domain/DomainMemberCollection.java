/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.domain;

import com.google.common.collect.FluentIterable;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Declaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RedefinableDeclaration;
import org.eclipse.xtext.resource.ISelectable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DomainMemberCollection {
    public static final DomainMemberCollection EMPTY = new DomainMemberCollection() {
        @Override
        public DomainMemberCollection merge(DomainMemberCollection domainMemberCollection) {
            return domainMemberCollection;
        }
    };

    protected final DomainMemberCollection parent;
    protected final DomainDeclaration domainDeclaration;
    protected final RedefinitionHandler redefinitionHandler;

    public final List<DeclarationHolder> declarationHolders = new ArrayList<>();
    public final Map<Declaration, DeclarationHolder> redefinitions = new HashMap<>();
    public final List<Declaration> declarations = new ArrayList<>();

    protected DomainMemberCollection() {
        this.domainDeclaration = null;
        this.parent = null;
        this.redefinitionHandler = null;
    }

    public DomainMemberCollection(DomainDeclaration domainDeclaration, DomainMemberCollection parent, RedefinitionHandler redefinitionHandler) {
        this.domainDeclaration = domainDeclaration;
        this.parent = parent;
        this.redefinitionHandler = redefinitionHandler;

        initFrom(parent);
        addMembers();
    }

    // FIXME: this does not work!
    public DomainMemberCollection merge(DomainMemberCollection domainMemberCollection) {
        var merged = new DomainMemberCollection(null, this, redefinitionHandler);

        merged.addMembers(FluentIterable.from(domainMemberCollection.declarationHolders).transform(DeclarationHolder::getDeclaration));

        return merged;
    }

    public ISelectable getMembers() {
        return new DomainMemberSelectable(this);
    }

    private void addMembers() {
        var members = OxstsUtils.getDirectMembers(domainDeclaration);
        addMembers(members);
    }

    private <T extends Declaration> void addMembers(Iterable<T> declarations) {
        for (var declaration : declarations) {
            addMember(declaration);
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

    protected void initFrom(DomainMemberCollection collection) {
        declarations.addAll(collection.declarations);

        var declarationHolderMap = new HashMap<DeclarationHolder, DeclarationHolder>();

        for (var declaration : collection.declarationHolders) {
            var clonedHolder = new DeclarationHolder(declaration.getDeclaration());
            declarationHolders.add(clonedHolder);
            declarationHolderMap.put(declaration, clonedHolder);
        }

        for (var entry : collection.redefinitions.entrySet()) {
            var decl = entry.getKey();
            var holder = entry.getValue();
            var clonedHolder = declarationHolderMap.get(holder);
            redefinitions.put(decl, clonedHolder);
        }
    }

    protected void add(Declaration declaration) {
        declarations.add(declaration);
        var declarationHolder = new DeclarationHolder(declaration);
        declarationHolders.add(declarationHolder);
        redefinitions.put(declaration, declarationHolder);
    }

    protected void add(Declaration declaration, Declaration redefined) {
        declarations.add(declaration);
        var redefinedHolder = redefinitions.get(redefined);
        redefinedHolder.setDeclaration(declaration);
        redefinitions.put(declaration, redefinedHolder);
    }

}
