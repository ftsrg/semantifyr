/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.utils;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Package;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.util.OnChangeEvictingCache;

import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

// TODO: convert to singleton class with injects!
public class OxstsUtils {

    private static final OnChangeEvictingCache cache = new OnChangeEvictingCache();
    private static final String BASE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils";
    private static final String getDirectlyAccessibleElementsReflective_KEY = BASE_KEY + ".getDirectlyAccessibleElementsReflective";


    public static Collection<Type> getAllTypesTransitive(final hu.bme.mit.semantifyr.oxsts.model.oxsts.Package _package) {
        return OxstsUtils.streamAllImportedPackages(_package).flatMap(p -> p.getTypes().stream()).toList();
    }

    public static Collection<Pattern> getAllPatternsTransitive(Package _package) {
        return OxstsUtils.streamAllImportedPackages(_package).flatMap(p -> p.getPatterns().stream()).toList();
    }

    public static Collection<? extends Element> getAllElementsTransitive(Package _package) {
        return OxstsUtils.streamAllImportedPackages(_package)
                .flatMap(p ->
                        Stream.of(
                                p.getPatterns(),
                                p.getTypes(),
                                p.getEnums()
                        ).flatMap(Collection::stream)
                ).toList();
    }

    public static Type getReferencedType(ReferenceTyping typing) {
        var chain = typing.getReference();
        var referencedElement = chain.getChains().getLast().getElement();
        if (referencedElement instanceof Type type) {
            return type;
        }

        return null;
    }

    public static Stream<Package> streamAllImportedPackages(final Package _package) {
        return Stream.concat(
                Stream.of(_package),
                _package.getImports().stream().map(Import::getPackage)
        );
    }

    public static Iterable<EObject> allContainersIterable(final EObject eObject) {
        return () -> new Iterator<>() {
            private EObject next = eObject;

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public EObject next() {
                var current = next;
                next = next.eContainer();
                return current;
            }
        };
    }

    public static Stream<EObject> streamAllContainers(final EObject eObject) {
        return StreamSupport.stream(allContainersIterable(eObject).spliterator(), false);
    }

    public static Stream<Namespace> streamAllContainerNamespaces(final EObject eObject) {
        return streamAllContainers(eObject).filter(e -> e instanceof Namespace).map(e -> (Namespace) e);
    }

}
