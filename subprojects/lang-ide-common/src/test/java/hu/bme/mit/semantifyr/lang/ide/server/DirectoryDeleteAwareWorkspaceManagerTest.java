/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.ide.server.ProjectManager;
import org.eclipse.xtext.workspace.IProjectConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DirectoryDeleteAwareWorkspaceManagerTest {

    private static final URI MODEL = URI.createURI("file:///workspace/lamp.oxsts");

    private static final URI WITNESS =
            URI.createURI("file:/tmp/semantifyr-1/lamp.Safe/verification/theta/witness.oxsts");

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
                        URI.createURI("file:///tmp/semantifyr-1/lamp.Safe/verification/theta/witness.oxsts")))
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
}
