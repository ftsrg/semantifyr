/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.backends.theta.verification.transformation.xsts.OxstsTransformer;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts;
import hu.bme.mit.semantifyr.semantics.transformation.serializer.CompilationStateManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Singleton
public class CompileInlinedOxstsCommandHandler extends AbstractCommandHandler<InlinedOxsts> {

    @Inject
    protected Provider<OxstsTransformer> oxstsTransformerProvider;

    @Inject
    protected Provider<CompilationStateManager> compilationStateManagerProvider;

    @Override
    public String getId() {
        return "oxsts.inlined.compile";
    }

    @Override
    public String getTitle() {
        return "Compile";
    }

    @Override
    public List<Object> serializeArguments(InlinedOxsts arguments) {
        var location = getLocation(arguments);
        return List.of(location);
    }

    @Override
    protected InlinedOxsts parseArguments(List<Object> arguments, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        var gsonBuilder = new GsonBuilder();
        var gson = gsonBuilder.create();
        var locationJson = (JsonObject) arguments.get(0);
        var location = gson.fromJson(locationJson, Location.class);
        var element = getElement(access, location);

        try {
            return (InlinedOxsts) element.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Object execute(InlinedOxsts arguments, ILanguageServerAccess access, CommandProgressContext progressContext) {
        compilationScopeRunnable(() -> {
            compilationStateManagerProvider.get().setSerializeSteps(false);
            var xsts = oxstsTransformerProvider.get().transform(arguments, false);

            try {
                xsts.eResource().save(null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        return null;
    }

}
