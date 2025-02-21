/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.utils;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Package;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.util.OnChangeEvictingCache;
import org.eclipse.xtext.util.Tuples;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class OxstsUtils {

    private static final OnChangeEvictingCache cache = new OnChangeEvictingCache();
    private static final String BASE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils";
    private static final String getDirectlyAccessibleElementsReflective_KEY = BASE_KEY + ".getDirectlyAccessibleElementsReflective";

    public static Iterable<EObject> getAllContainers(final EObject eObject) {
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

    public static Iterable<Type> getAllSupertypes(final Type type) {
        return () -> new Iterator<>() {
            private Type next = type;

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public Type next() {
                var current = next;
                next = next.getSupertype();
                return current;
            }
        };
    }

    public static Stream<? extends Element> getAccessibleElements(EObject element, EClass eClass, boolean hierarchy) {
        if (element == null) {
            return Stream.empty();
        }

        Stream<? extends Element> accessibleElements;

        if (hierarchy) {
            var containers = getAllContainers(element).spliterator();

            accessibleElements = StreamSupport.stream(containers, false)
                    .flatMap(e -> getDirectlyAccessibleElements(e, eClass));
        } else {
            accessibleElements = getDirectlyAccessibleElements(element, eClass);
        }

        return accessibleElements
                .filter(Objects::nonNull)
                .distinct();
    }

    private static Stream<? extends Element> getDirectlyAccessibleElements(EObject element, EClass eClass) {
        if (element == null) {
            return Stream.empty();
        }

        return switch (element) {
            case Package _package -> Stream.concat(
                    getDirectlyAccessibleElementsReflective(_package, eClass),
                    getImportedElements(_package, eClass)
            );
            case Type type -> getInheritedElements(type, eClass);
            case Containment containment -> Stream.concat(
                    getDirectlyAccessibleElementsReflective(containment, eClass),
                    getInheritedElements(containment.getTyping(), eClass)
            );
            case Feature feature -> getInheritedElements(feature.getTyping(), eClass);
            case Parameter parameter -> getInheritedElements(parameter.getType(), eClass);
            case Argument argument -> getInheritedElements(argument.getTyping(), eClass);
            default -> getDirectlyAccessibleElementsReflective(element, eClass);
        };
    }

    private static Stream<? extends Element> getImportedElements(Package _package, EClass eClass) {
        return _package.getImports().stream().flatMap(it -> getDirectlyAccessibleElementsReflective(it.getPackage(), eClass));
    }

    private static Stream<? extends Element> getDirectlyAccessibleElementsReflective(EObject eObject, EClass eClass) {
        return cache.get(Tuples.create(getDirectlyAccessibleElementsReflective_KEY, eObject, eClass), eObject.eResource(), () ->
            collectDirectlyAccessibleElementsReflective(eObject, eClass)
        ).stream(); // stream out the collection from cache
    }

    private static Collection<? extends Element> collectDirectlyAccessibleElementsReflective(EObject eObject, EClass eClass) {
        return eObject.eClass().getEAllContainments().stream()
            .filter(containment -> eClass.isSuperTypeOf(containment.getEReferenceType()))
            .flatMap(containment -> {
                try {
                    var element = eObject.eGet(containment);

                    if (element instanceof EList<?> list) {
                        //noinspection unchecked
                        return (Stream<? extends Element>) list.stream();
                    }

                    return Stream.of((Element) element);
                } catch (Throwable throwable) {
                    // If for some reason the features are not resolvable, fail gracefully.
                    return Stream.empty();
                }
            }).toList();
    }

    private static Stream<? extends Element> getInheritedElements(Typing typing, EClass eClass) {
        if (typing instanceof ReferenceTyping referenceTyping) {
            var chain = referenceTyping.getReference();
            var lastExpression = chain.getChains().getLast();
            var referencedElement = getReferredElement(lastExpression);
            if (referencedElement instanceof Type type) {
                return getInheritedElements(type, eClass);
            }
        }

        return Stream.empty();
    }

    private static Stream<? extends Element> getInheritedElements(Type type, EClass eClass) {
        if (type == null) {
            return Stream.empty();
        }

        var supertypes = getAllSupertypes(type).spliterator();

        return StreamSupport.stream(supertypes, false).flatMap(it ->
                getDirectlyAccessibleElementsReflective(it, eClass)
        );
    }

    public static Element getReferredElement(ReferenceExpression expression) {
        if (expression instanceof DeclarationReferenceExpression declarationReference) {
            return declarationReference.getElement();
        } else if (expression instanceof ChainReferenceExpression chainReference) {
            return getReferredElement(chainReference.getChains().getLast());
        } else {
            return null;
        }
    }

    public static Element getReferredElement(ChainingExpression expression) {
        if (expression instanceof DeclarationReferenceExpression declarationReferenceExpression) {
            return declarationReferenceExpression.getElement();
        }

        throw new IllegalStateException("");
    }

}
