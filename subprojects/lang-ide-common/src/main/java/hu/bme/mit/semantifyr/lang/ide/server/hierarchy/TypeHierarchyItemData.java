/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.hierarchy;

import com.google.gson.JsonElement;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandGson;
import org.eclipse.lsp4j.TypeHierarchyItem;

// The document the hierarchy was prepared in selects the resource set the element is resolved in.
public record TypeHierarchyItemData(String documentUri, String elementUri) {

    public static TypeHierarchyItemData of(TypeHierarchyItem item) {
        return switch (item.getData()) {
            case TypeHierarchyItemData data -> data;
            case JsonElement json -> CommandGson.INSTANCE.fromJson(json, TypeHierarchyItemData.class);
            case null, default -> throw new IllegalArgumentException("Type hierarchy item carries no element");
        };
    }
}
