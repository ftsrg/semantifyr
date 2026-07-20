/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.lang.ide.server.commands.AbstractCommandHandler;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandProgressContext;
import hu.bme.mit.semantifyr.oxsts.lang.ide.client.NavigateToParams;
import hu.bme.mit.semantifyr.oxsts.lang.ide.client.SemantifyrLanguageClient;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinersFinder;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RedefinableDeclaration;
import java.util.List;
import org.eclipse.lsp4j.Location;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;

public class NavigateToRedefinersCommandHandler extends AbstractCommandHandler<Location, RedefinableDeclaration> {

    @Inject
    private RedefinersFinder redefinersFinder;

    @Override
    public String getId() {
        return "oxsts.redefinable.redefiners.navigate";
    }

    @Override
    public String getTitle() {
        return "↓ Go To Redefiners";
    }

    @Override
    public List<Object> serializeArguments(RedefinableDeclaration arguments) {
        return List.of(getLocation(arguments));
    }

    @Override
    protected Class<Location> getRequestType() {
        return Location.class;
    }

    @Override
    protected RedefinableDeclaration resolveArgument(
            Location request, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        return (RedefinableDeclaration) getElement(access, request);
    }

    @Override
    protected Object execute(
            RedefinableDeclaration arguments, ILanguageServerAccess access, CommandProgressContext progressContext) {
        var client = (SemantifyrLanguageClient) access.getLanguageClient();

        var redefiners = redefinersFinder.getRedefinerDeclarations(arguments);

        var locations = redefiners.stream().map(this::getLocation).toList();

        if (!locations.isEmpty()) {
            client.navigateTo(new NavigateToParams(locations));
        }

        return null;
    }
}
