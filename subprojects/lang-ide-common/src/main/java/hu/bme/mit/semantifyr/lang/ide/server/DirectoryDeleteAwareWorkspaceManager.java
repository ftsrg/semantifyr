/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.ide.server.BuildManager;
import org.eclipse.xtext.ide.server.ProjectManager;
import org.eclipse.xtext.ide.server.WorkspaceManager;

public class DirectoryDeleteAwareWorkspaceManager extends WorkspaceManager {

    @Inject
    protected WitnessOwners witnessOwners;

    // a witness under temporary artifacts lies outside every project and links against the project of its case
    @Override
    public ProjectManager getProjectManager(URI uri) {
        var projectManager = super.getProjectManager(uri);
        if (projectManager != null) {
            return projectManager;
        }
        var caseResource = witnessOwners.caseResourceOrNull(uri);
        if (caseResource == null) {
            return null;
        }
        return super.getProjectManager(caseResource);
    }

    @Override
    public BuildManager.Buildable didChangeFiles(List<URI> dirtyFiles, List<URI> deletedFiles) {
        var allDeletedFiles = new ArrayList<>(deletedFiles);
        for (var deleted : deletedFiles) {
            if (deleted.fileExtension() == null) {
                var prefix = deleted.isPrefix() ? deleted : deleted.appendSegment("");
                var projectManager = getProjectManager(deleted);
                var resourceSet = projectManager.getResourceSet();
                var innerFiles = resourceSet.getResources().stream()
                        .map(Resource::getURI)
                        .filter(uri -> UriUtils.startsWith(uri, prefix))
                        .toList();

                allDeletedFiles.addAll(innerFiles);
            }
        }

        return super.didChangeFiles(dirtyFiles, allDeletedFiles);
    }
}
