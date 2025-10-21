/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.ide.server.BuildManager;
import org.eclipse.xtext.ide.server.WorkspaceManager;

import java.util.ArrayList;
import java.util.List;

public class OxstsWorkspaceManager extends WorkspaceManager {

    @Override
    public BuildManager.Buildable didChangeFiles(List<URI> dirtyFiles, List<URI> deletedFiles) {
        var allDeletedFiles = new ArrayList<>(deletedFiles);
        for (var deleted : deletedFiles) {
            if (deleted.fileExtension() == null) {
                var prefix = deleted.isPrefix() ? deleted : deleted.appendSegment("");
                var projectManager = getProjectManager(deleted);
                var resourceSet = projectManager.getResourceSet();
                var innerFiles = resourceSet.getResources().stream().map(Resource::getURI).filter(uri -> UriUtils.startsWith(uri, prefix)).toList();

                allDeletedFiles.addAll(innerFiles);
            }
        }

        return super.didChangeFiles(dirtyFiles, allDeletedFiles);
    }


}
