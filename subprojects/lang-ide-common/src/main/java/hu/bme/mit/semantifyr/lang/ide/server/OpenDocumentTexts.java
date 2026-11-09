/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.xtext.ide.server.WorkspaceManager;

public class OpenDocumentTexts {

    @Inject
    private WorkspaceManager workspaceManager;

    public Map<URI, String> of(ResourceSet resourceSet) {
        var texts = new HashMap<URI, String>();
        for (var resource : List.copyOf(resourceSet.getResources())) {
            var uri = resource.getURI();
            if (workspaceManager.isDocumentOpen(uri)) {
                texts.put(uri, workspaceManager.doRead(uri, (document, xtextResource) -> document.getContents()));
            }
        }
        return texts;
    }
}
