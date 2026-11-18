/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.xtext.ide.server.BuildManager;
import org.eclipse.xtext.ide.server.ProjectManager;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.workspace.IProjectConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DirectoryDeleteAwareWorkspaceManagerTest {

    private static final URI MODEL = URI.createURI("file:///workspace/lamp.semantifyr");

    private static final URI WITNESS =
            URI.createURI("file:/tmp/semantifyr-1/lamp.Safe/verification/theta/witness.semantifyr");

    private final ProjectManager project = mock(ProjectManager.class);

    private final IProjectConfig projectConfig = mock(IProjectConfig.class);

    private final DirectoryDeleteAwareWorkspaceManager workspaceManager = new DirectoryDeleteAwareWorkspaceManager() {
        @Override
        protected IProjectConfig getProjectConfig(URI uri) {
            return UriUtils.isSameFile(uri, MODEL) ? projectConfig : null;
        }

        @Override
        public ProjectManager getProjectManager(String projectName) {
            return "workspace".equals(projectName) ? project : null;
        }
    };

    @BeforeEach
    void setUp() {
        when(projectConfig.getName()).thenReturn("workspace");
        workspaceManager.witnessOwners = new WitnessOwners();
    }

    @Test
    void aWitnessOutsideEveryProjectBelongsToTheProjectOfItsCase() {
        workspaceManager.witnessOwners.record(WITNESS, MODEL);

        assertThat(workspaceManager.getProjectManager(WITNESS)).isSameAs(project);
    }

    @Test
    void theUriFormWithAnEmptyAuthorityNamesTheSameWitness() {
        workspaceManager.witnessOwners.record(WITNESS, MODEL);

        assertThat(workspaceManager.getProjectManager(
                        URI.createURI("file:///tmp/semantifyr-1/lamp.Safe/verification/theta/witness.semantifyr")))
                .isSameAs(project);
    }

    @Test
    void aFileOutsideEveryProjectWithoutACaseHasNoProject() {
        assertThat(workspaceManager.getProjectManager(WITNESS)).isNull();
    }

    @Test
    void aFileInAProjectKeepsItsProject() {
        assertThat(workspaceManager.getProjectManager(MODEL)).isSameAs(project);
    }

    @Test
    void deletingADirectoryDeletesEveryResourceBelowIt() {
        var lamp = URI.createURI("file:///workspace/lights/lamp.semantifyr");
        var nested = URI.createURI("file:///workspace/lights/inner/switch.semantifyr");
        var sibling = URI.createURI("file:///workspace/lightsaber.semantifyr");
        var resourceSet = new XtextResourceSet();
        for (var uri : List.of(lamp, nested, sibling)) {
            resourceSet.getResources().add(new ResourceImpl(uri));
        }
        when(project.getResourceSet()).thenReturn(resourceSet);
        var buildManager = mock(BuildManager.class);
        var directoryDeleteAwareManager = new DirectoryDeleteAwareWorkspaceManager() {
            @Override
            public ProjectManager getProjectManager(URI uri) {
                return project;
            }
        };
        directoryDeleteAwareManager.setBuildManager(buildManager);
        var folder = URI.createURI("file:///workspace/lights");

        directoryDeleteAwareManager.didChangeFiles(List.of(), List.of(folder));

        verify(buildManager).submit(List.of(), List.of(folder, lamp, nested));
    }
}
