/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.utils;

import java.util.HashSet;
import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

public final class SourceLocation {

    private SourceLocation() {
        throw new IllegalStateException("This is a static utility class and should not be instantiated directly");
    }

    public static void recordOrigin(EObject original, EObject derived) {
        var origin = originOf(original);
        derived.eAdapters().removeIf(adapter -> adapter instanceof CopyOrigin);
        derived.eAdapters().add(new CopyOrigin(origin));
    }

    public static String prefixFor(EObject eObject) {
        var located = locatedElementOf(eObject);
        var node = findNode(located);

        Resource resource = null;
        try {
            resource = located.eResource();
        } catch (Throwable ignored) {

        }

        var fileName = (resource != null && resource.getURI() != null)
                ? resource.getURI().lastSegment()
                : null;

        if (node == null && fileName == null) {
            return "";
        }

        var file = fileName != null ? fileName : "<unknown>";
        var line = node != null ? String.valueOf(node.getStartLine()) : "?";
        return file + ":" + line + ": ";
    }

    // a copy has no node and is located at its origin, a created element at its container
    private static EObject locatedElementOf(EObject eObject) {
        var visited = new HashSet<EObject>();
        var current = eObject;
        while (current != null && visited.add(current)) {
            current = originOf(current);
            if (findNode(current) != null) {
                return current;
            }
            current = current.eContainer();
        }
        return eObject;
    }

    private static EObject originOf(EObject eObject) {
        var adapters = eObject.eAdapters();
        if (adapters == null) {
            return eObject;
        }
        for (var adapter : adapters) {
            if (adapter instanceof CopyOrigin copyOrigin) {
                return copyOrigin.original;
            }
        }
        return eObject;
    }

    private static ICompositeNode findNode(EObject eObject) {
        try {
            return NodeModelUtils.findActualNodeFor(eObject);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class CopyOrigin extends AdapterImpl {
        private final EObject original;

        private CopyOrigin(EObject original) {
            this.original = original;
        }
    }
}
