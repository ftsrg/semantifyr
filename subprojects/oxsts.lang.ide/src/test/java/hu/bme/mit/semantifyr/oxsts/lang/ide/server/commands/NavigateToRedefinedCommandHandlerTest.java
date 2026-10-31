/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import hu.bme.mit.semantifyr.oxsts.lang.ide.client.NavigateToParams;
import hu.bme.mit.semantifyr.oxsts.lang.ide.client.SemantifyrLanguageClient;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RedefinableDeclaration;
import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.xtext.ide.server.DocumentExtensions;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.resource.IResourceServiceProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NavigateToRedefinedCommandHandlerTest {

    @Mock
    private RedefinitionHandler redefinitionHandler;

    @Mock
    private IResourceServiceProvider.Registry registry;

    @Mock
    private SemantifyrLanguageClient client;

    @Mock
    private ILanguageServerAccess access;

    @Mock
    private RedefinableDeclaration declaration;

    @InjectMocks
    private NavigateToRedefinedCommandHandler handler;

    @BeforeEach
    void setUp() {
        when(access.getLanguageClient()).thenReturn(client);
    }

    @Test
    void aDeclarationThatRedefinesNothingNavigatesNowhere() {
        when(redefinitionHandler.getRedefinedDeclaration(declaration)).thenReturn(null);

        assertThat(handler.execute(declaration, access, null)).isNull();

        verifyNoInteractions(client);
    }

    @Test
    void aRedefiningDeclarationNavigatesToTheRedefinedOne() {
        var redefined = mock(RedefinableDeclaration.class);
        var resource = mock(Resource.class);
        var uri = URI.createURI("file:///workspace/model.oxsts");
        var serviceProvider = mock(IResourceServiceProvider.class);
        var documentExtensions = mock(DocumentExtensions.class);
        var location = new Location(uri.toString(), new Range(new Position(1, 4), new Position(1, 9)));
        when(redefinitionHandler.getRedefinedDeclaration(declaration)).thenReturn(redefined);
        when(redefined.eResource()).thenReturn(resource);
        when(resource.getURI()).thenReturn(uri);
        when(registry.getResourceServiceProvider(uri)).thenReturn(serviceProvider);
        when(serviceProvider.get(DocumentExtensions.class)).thenReturn(documentExtensions);
        when(documentExtensions.newLocation(redefined)).thenReturn(location);

        assertThat(handler.execute(declaration, access, null)).isNull();

        verify(client).navigateTo(new NavigateToParams(List.of(location)));
    }
}
