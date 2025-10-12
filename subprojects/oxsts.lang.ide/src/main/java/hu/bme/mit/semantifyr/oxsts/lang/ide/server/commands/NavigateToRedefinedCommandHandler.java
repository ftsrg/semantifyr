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
import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RedefinableDeclaration;
import org.eclipse.lsp4j.Location;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class NavigateToRedefinedCommandHandler extends AbstractCommandHandler<RedefinableDeclaration> {

    @Inject
    private RedefinitionHandler redefinitionHandler;

    @Override
    public String getId() {
        return "oxsts.redefinable.redefined.navigate";
    }

    @Override
    public String getTitle() {
        return "Go To Redefined";
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

        try {
            return (RedefinableDeclaration) element.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Object execute(RedefinableDeclaration arguments, ILanguageServerAccess access, CommandProgressContext progressContext) {
        var client = (OxstsLanguageClient) access.getLanguageClient();

        var redefined = redefinitionHandler.getRedefinedDeclaration(arguments);

        var location = getLocation(redefined);

        client.navigateTo(new NavigateToParams(location));

        return null;
    }

}
