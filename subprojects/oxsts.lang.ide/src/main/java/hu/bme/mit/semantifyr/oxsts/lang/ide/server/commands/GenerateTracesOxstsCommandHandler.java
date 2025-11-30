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
import hu.bme.mit.semantifyr.backends.theta.verification.backannotation.ThetaSummaryGenerator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.semantics.transformation.serializer.CompilationStateManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Singleton
public class GenerateTracesOxstsCommandHandler extends AbstractCommandHandler<ClassDeclaration> {

    @Inject
    protected Provider<ThetaSummaryGenerator> thetaSummaryGeneratorProvider;

    @Inject
    protected Provider<CompilationStateManager> compilationStateManagerProvider;

    @Override
    public String getId() {
        return "oxsts.case.tracegen";
    }

    @Override
    public String getTitle() {
        return "Generate Traces";
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
        progressContext.begin("Summarizing class " + arguments.getName(), "Initializing");

        var result = new CompletableFuture<Void>();

        try {
            runLongRunningInCompilationScope(arguments, (classDeclaration) -> {
                progressContext.checkIsCancelled();

                compilationStateManagerProvider.get().setSerializeSteps(false);
                thetaSummaryGeneratorProvider.get().createSummary(progressContext, classDeclaration);
                result.complete(null);
            });
        } catch (Exception e) {
            result.completeExceptionally(e);
        }

        try {
            result.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return null;
    }

}
