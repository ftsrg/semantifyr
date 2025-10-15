/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.semantics.transformation.OxstsToXstsTransformer;
import hu.bme.mit.semantifyr.semantics.transformation.serializer.CompilationStateManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;

import java.util.List;

@Singleton
public class InlineOxstsCommandHandler extends AbstractCommandHandler<ClassDeclaration> {

    @Inject
    protected Provider<OxstsToXstsTransformer> xstsTransformerProvider;

    @Inject
    protected Provider<CompilationStateManager> compilationStateManagerProvider;

    @Override
    public String getId() {
        return "oxsts.class.inline";
    }

    @Override
    public String getTitle() {
        return "Inline (with steps)";
    }

    @Override
    public List<Object> serializeArguments(ClassDeclaration arguments) {
        var location = getLocation(arguments);
        return List.of(location);
    }

    @Override
    protected ClassDeclaration parseArguments(List<Object> arguments, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        var gsonBuilder = new GsonBuilder();
        var gson = gsonBuilder.create();
        var locationJson = (JsonObject) arguments.get(0);
        var location = gson.fromJson(locationJson, Location.class);
        var element = getElement(access, location);

        return (ClassDeclaration) element;
    }

    @Override
    protected Object execute(ClassDeclaration arguments, ILanguageServerAccess access, CommandProgressContext progressContext) {
        progressContext.begin("Compiling class " + arguments.getName(), "Initializing");

        runLongRunningInCompilationScope(arguments, (inlinedOxsts) -> {
            progressContext.checkIsCancelled();

            compilationStateManagerProvider.get().setSerializeSteps(true);
            xstsTransformerProvider.get().transform(progressContext, inlinedOxsts);
        });

        return null;
    }

}
