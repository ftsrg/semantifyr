/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.utils;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

public final class SourceLocation {

    private SourceLocation() {
        throw new IllegalStateException("This is a static utility class and should not be instantiated directly");
    }

    public static String prefixFor(EObject eObject) {
        ICompositeNode node = null;
        try {
            node = NodeModelUtils.findActualNodeFor(eObject);
        } catch (Throwable ignored) {

        }

        Resource resource = null;
        try {
            resource = eObject.eResource();
        } catch (Throwable ignored) {

        }

        var fileName = (resource != null && resource.getURI() != null) ? resource.getURI().lastSegment() : null;

        if (node == null && fileName == null) {
            return "";
        }

        var file = fileName != null ? fileName : "<unknown>";
        var line = node != null ? String.valueOf(node.getStartLine()) : "?";
        return file + ":" + line + ": ";
    }

}
