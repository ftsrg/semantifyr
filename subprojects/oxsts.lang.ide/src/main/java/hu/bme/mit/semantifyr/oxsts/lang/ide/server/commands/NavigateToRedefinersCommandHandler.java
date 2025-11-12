/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.ide.client.NavigateToParams;
import hu.bme.mit.semantifyr.oxsts.lang.ide.client.OxstsLanguageClient;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinersFinder;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RedefinableDeclaration;
import org.eclipse.lsp4j.Location;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;

import java.util.List;

public class NavigateToRedefinersCommandHandler extends AbstractCommandHandler<RedefinableDeclaration> {

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
        var location = getLocation(arguments);
        return List.of(location);
    }

    @Override
    protected RedefinableDeclaration parseArguments(List<Object> arguments, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        var gsonBuilder = new GsonBuilder();
        var gson = gsonBuilder.create();
        var locationJson = (JsonObject) arguments.get(0);
        var location = gson.fromJson(locationJson, Location.class);
        var element = getElement(access, location);

        return (RedefinableDeclaration) element;
    }

    @Override
    protected Object execute(RedefinableDeclaration arguments, ILanguageServerAccess access, CommandProgressContext progressContext) {
        var client = (OxstsLanguageClient) access.getLanguageClient();

        var redefiners = redefinersFinder.getRedefinerDeclarations(arguments);

        var locations = redefiners.stream().map(this::getLocation).toList();

        if (!locations.isEmpty()) {
            client.navigateTo(new NavigateToParams(locations));
        }

        return null;
    }

}
