/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.gamma;

import org.eclipse.emf.ecore.EObject;

import java.util.Arrays;
import java.util.Collection;
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

    @SafeVarargs
    public static <T> Stream<T> streamOfCollections(Collection<? extends T>... collections) {
        return Arrays.stream(collections).flatMap(Collection::stream);
    }

    public static Stream<? extends EObject> getAccessibleElements(EObject element) {
        if (element == null) {
            return Stream.empty();
        }

        var containers = getAllContainers(element).spliterator();

        return StreamSupport.stream(containers, false).flatMap(GammaUtils::getLocalAccessibleElements);
    }

    public static Stream<? extends EObject> getLocalAccessibleElements(EObject element) {
        if (element == null) {
            return Stream.empty();
        }

        return switch (element) {
            case Package _package -> getLocalAccessibleElements(_package);
            case ComponentInstance instance -> getInheritedElements(instance);
            case Component component -> getLocalAccessibleElements(component);
            default -> Stream.empty();
        };
    }

    public static Stream<? extends EObject> getLocalAccessibleElements(Package _package) {
        return _package.getComponents().stream();
    }

    public static Stream<? extends EObject> getInheritedElements(ComponentInstance componentInstance) {
        if (componentInstance == null) {
            return Stream.empty();
        }

        return getLocalAccessibleElements(componentInstance.getComponent());
    }

    public static Stream<? extends EObject> getLocalAccessibleElements(Component component) {
        return switch (component) {
            case SyncComponent syncComponent -> getLocalAccessibleElements(syncComponent);
            case Statechart statechart -> getLocalAccessibleElements(statechart);
            default -> Stream.empty();
        };
    }

    public static Stream<? extends EObject> getLocalAccessibleElements(SyncComponent syncComponent) {
        if (syncComponent == null) {
            return Stream.empty();
        }

        return streamOfCollections(
                syncComponent.getEvents(),
                syncComponent.getComponents()
        );
    }

    public static Stream<? extends EObject> getLocalAccessibleElements(Statechart statechart) {
        if (statechart == null) {
            return Stream.empty();
        }

        return streamOfCollections(
                statechart.getEvents(),
                statechart.getTimeouts(),
                statechart.getRegions(),
                statechart.getVariables()
        );
    }

}
