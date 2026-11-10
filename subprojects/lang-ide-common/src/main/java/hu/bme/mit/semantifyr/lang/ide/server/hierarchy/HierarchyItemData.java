/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.hierarchy;

import com.google.gson.JsonElement;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandGson;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;

// The document the hierarchy was prepared in selects the resource set the element is resolved in.
public record HierarchyItemData(String documentUri, String elementUri) {

    public static HierarchyItemData of(Object data) {
        return switch (data) {
            case HierarchyItemData itemData -> itemData;
            case JsonElement json -> CommandGson.INSTANCE.fromJson(json, HierarchyItemData.class);
            case null, default -> throw new IllegalArgumentException("Hierarchy item carries no element");
        };
    }

    public EObject resolve(Resource documentResource) {
        return documentResource.getResourceSet().getEObject(URI.createURI(elementUri), true);
    }
}
