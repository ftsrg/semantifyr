/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.gamma;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EcorePackage;

import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class GammaUtils {

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

    public static Stream<? extends EObject> getAccessibleElements(EObject element, EClass eClass, boolean hierarchy) {
        if (element == null) {
            return Stream.empty();
        }

        if (hierarchy) {
            var containers = getAllContainers(element).spliterator();

            return StreamSupport.stream(containers, false)
                    .flatMap(e -> getDirectlyAccessibleElements(e, eClass))
                    .distinct();
        } else {
            return getDirectlyAccessibleElements(element, eClass).distinct();
        }
    }

    public static Stream<? extends EObject> getDirectlyAccessibleElements(EObject element, EClass eClass) {
        if (element == null) {
            return Stream.empty();
        }

        return switch (element) {
            case ComponentInstance instance -> getInheritedElements(instance, eClass);
            case Package _package -> getDirectlyAccessibleElementsReflective(_package, eClass);
            case Component component -> getDirectlyAccessibleElementsReflective(component, eClass);
            case Region region -> getDirectlyAccessibleElementsReflective(region, eClass);
            case State state -> getDirectlyAccessibleElementsReflective(state, eClass);
            default -> Stream.empty();
        };
    }

    public static Stream<? extends EObject> getInheritedElements(ComponentInstance componentInstance, EClass eClass) {
        if (componentInstance == null) {
            return Stream.empty();
        }

        return getDirectlyAccessibleElementsReflective(componentInstance.getComponent(), eClass);
    }

    public static Stream<? extends EObject> getDirectlyAccessibleElementsReflective(EObject eObject, EClass eClass) {
        return eObject.eClass().getEAllContainments().stream()
                .filter(containment ->
                        eClass == EcorePackage.eINSTANCE.getEObject() || eClass.isSuperTypeOf(containment.getEReferenceType())
                )
                .flatMap(containment -> {
                    try {
                        var element = eObject.eGet(containment);

                        if (element instanceof EList<?> list) {
                            //noinspection unchecked
                            return (Stream<? extends EObject>) list.stream();
                        }

                        return Stream.of((EObject) element);
                    } catch (Throwable throwable) {
                        // If for some reason the features are not resolvable, fail gracefully.
                        return Stream.empty();
                    }
                });

    }

}
