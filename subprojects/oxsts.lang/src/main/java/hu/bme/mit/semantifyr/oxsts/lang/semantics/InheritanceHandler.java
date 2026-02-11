/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OnResourceSetChangeEvictingCache;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.xtext.util.IResourceScopeCache;
import org.eclipse.xtext.util.Tuples;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class InheritanceHandler {
    private static final String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.semantics.InheritanceHandler.CACHE_KEY";

    @Inject
    private OnResourceSetChangeEvictingCache cache;

    @Inject
    protected RedefinitionHandler redefinitionHandler;

    @Inject
    protected SubsetHandler subsetHandler;

    @Inject
    protected BuiltinSymbolResolver builtinSymbolResolver;

    public List<? extends DomainDeclaration> getSuperDomains(DomainDeclaration declaration) {
        return cache.get(Tuples.create(CACHE_KEY, declaration), declaration.eResource(), () -> computeSuperDomains(declaration));
    }

    protected List<? extends DomainDeclaration> computeSuperDomains(DomainDeclaration domainDeclaration) {
        return switch (domainDeclaration) {
            case FeatureDeclaration domain -> computeSuperDomains(domain);
            case ClassDeclaration domain -> computeSuperDomains(domain);
            case RecordDeclaration domain -> List.of();
            case DataTypeDeclaration domain -> List.of();
            case EnumDeclaration domain -> List.of();
            case InlinedOxsts domain -> List.of();
            default -> throw new IllegalStateException("Unexpected value: " + domainDeclaration);
        };
    }

    protected List<? extends DomainDeclaration> computeSuperDomains(ClassDeclaration classDeclaration) {
        if (classDeclaration.getSuperTypes().isEmpty()) {
            if (builtinSymbolResolver.isAnythingClass(classDeclaration)) {
                return List.of();
            }

            return List.of(builtinSymbolResolver.anythingClass(classDeclaration));
        }

        return classDeclaration.getSuperTypes().stream().filter(k -> ! k.eIsProxy()).toList();
    }

    protected List<? extends DomainDeclaration> computeSuperDomains(FeatureDeclaration featureDeclaration) {
        var superDomains = new ArrayList<DomainDeclaration>();

        var domain = featureDeclaration.getTypeSpecification().getDomain();
        if (domain != null) {
            if (! domain.eIsProxy()) {
                superDomains.add(domain);
            }
        }

//        var redefined = redefinitionHandler.getRedefinedDeclaration(featureDeclaration);
//        if (redefined != null) {
//            superDomains.add(redefined);
//        }

//        for (var decl : superSetHandler.getSuperSetFeatures(featureDeclaration)) {
//            superDomains.add(decl);
//        }

        return superDomains;
    }

}
