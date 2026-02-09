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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.semantics.transformation.OxstsClassInliner;
import hu.bme.mit.semantifyr.semantics.transformation.serializer.CompilationStateManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;

import java.util.List;

@Singleton
public class InlineOxstsCommandHandler extends AbstractCommandHandler<InlineOxstsCommandParams> {

    @Inject
    protected Provider<OxstsClassInliner> xstsTransformerProvider;

    @Inject
    protected Provider<CompilationStateManager> compilationStateManagerProvider;

    @Override
    public String getId() {
        return "oxsts.class.inline";
    }

    @Override
    public String getTitle() {
        return "Inline";
    }

    @Override
    public List<Object> serializeArguments(InlineOxstsCommandParams arguments) {
        var location = getLocation(arguments.classDeclaration());
        var serializeSteps = arguments.serializeSteps();
        return List.of(location, serializeSteps);
    }

    @Override
    protected InlineOxstsCommandParams parseArguments(List<Object> arguments, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        var gsonBuilder = new GsonBuilder();
        var gson = gsonBuilder.create();
        var locationJson = (JsonObject) arguments.get(0);
        var location = gson.fromJson(locationJson, Location.class);
        var serializeStepsJson = (JsonPrimitive) arguments.get(1);
        var serializeSteps = gson.fromJson(serializeStepsJson, boolean.class);
        var element = getElement(access, location);

        return new InlineOxstsCommandParams((ClassDeclaration) element, serializeSteps);
    }

    @Override
    protected Object execute(InlineOxstsCommandParams arguments, ILanguageServerAccess access, CommandProgressContext progressContext) {
        progressContext.begin("Compiling class " + arguments.classDeclaration().getName(), "Transforming");

        runLongRunningInCompilationScope(arguments.classDeclaration(), (inlinedOxsts) -> {
            progressContext.checkIsCancelled();

            compilationStateManagerProvider.get().setSerializeSteps(arguments.serializeSteps());
            xstsTransformerProvider.get().inline(progressContext, inlinedOxsts);
        });

        return null;
    }

}
