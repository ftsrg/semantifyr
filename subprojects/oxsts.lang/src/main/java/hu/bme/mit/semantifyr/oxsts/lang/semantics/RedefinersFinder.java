/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OnResourceSetChangeEvictingCache;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.xtext.util.Tuples;

import java.util.*;

public class RedefinersFinder {

    private static final String CACHE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinersFinder.CACHE_KEY";

    @Inject
    private OnResourceSetChangeEvictingCache cache;

    @Inject
    protected RedefinitionHandler redefinitionHandler;

    public List<RedefinableDeclaration> getRedefinerDeclarations(RedefinableDeclaration redefinableDeclaration) {
        var redefinersCollection = getRedefinersCollection(redefinableDeclaration);
        return redefinersCollection.getRedefinersOf(redefinableDeclaration);
    }

    protected RedefinersCollection getRedefinersCollection(EObject context) {
        return cache.get(CACHE_KEY, context.eResource(), () -> computeRedefinersCollection(context));
    }

    protected RedefinersCollection computeRedefinersCollection(EObject context) {
        return new RedefinersCollection(context);
    }

    protected class RedefinersCollection {

        protected final Map<RedefinableDeclaration, List<RedefinableDeclaration>> redefinersCollection = new HashMap<>();

        public RedefinersCollection(EObject context) {
            var resourceSet = context.eResource().getResourceSet();

            var redefinableDeclarations = getAllContentsOfType(resourceSet, RedefinableDeclaration.class);

            for (var redefinableDeclaration : redefinableDeclarations) {
                addRedefiner(redefinableDeclaration);
            }
        }

        public static <T extends EObject> List<T> getAllContentsOfType(ResourceSet resourceSet, Class<T> type) {
            return Lists.newArrayList(Iterators.filter(resourceSet.getAllContents(), type));
        }

        protected void addRedefiner(RedefinableDeclaration redefiner, RedefinableDeclaration redefined) {
            var collection = redefinersCollection.computeIfAbsent(redefined, k -> new ArrayList<>());

            collection.add(redefiner);
        }

        protected void addRedefiner(RedefinableDeclaration redefinableDeclaration) {
            var redefined = redefinitionHandler.getRedefinedDeclaration(redefinableDeclaration);
            if (redefined != null) {
                addRedefiner(redefinableDeclaration, redefined);
            }
        }

        public List<RedefinableDeclaration> getRedefinersOf(RedefinableDeclaration redefinableDeclaration) {
            return redefinersCollection.getOrDefault(redefinableDeclaration, Collections.emptyList());
        }

    }

}
