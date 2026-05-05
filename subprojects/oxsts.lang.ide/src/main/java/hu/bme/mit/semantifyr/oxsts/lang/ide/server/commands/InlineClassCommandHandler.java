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
import com.google.inject.Injector;
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler;
import hu.bme.mit.semantifyr.lang.ide.server.ServerSettings;
import hu.bme.mit.semantifyr.lang.ide.server.commands.AbstractCommandHandler;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandProgressContext;
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import java.util.List;
import org.eclipse.lsp4j.Location;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InlineClassCommandHandler extends AbstractCommandHandler<InlineClassCommandParams> {

    private static final Logger LOGGER = LoggerFactory.getLogger(InlineClassCommandHandler.class);

    @Inject
    protected OxstsQualifiedNameProvider oxstsQualifiedNameProvider;

    @Inject
    protected ServerSettings serverSettings;

    @Inject
    protected Injector injector;

    @Override
    public String getId() {
        return "oxsts.class.inline";
    }

    @Override
    public String getTitle() {
        return "Inline";
    }

    @Override
    public List<Object> serializeArguments(InlineClassCommandParams arguments) {
        var location = getLocation(arguments.classDeclaration());
        var serializeSteps = arguments.serializeSteps();
        return List.of(location, serializeSteps);
    }

    @Override
    protected InlineClassCommandParams parseArguments(
            List<Object> arguments, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        var gsonBuilder = new GsonBuilder();
        var gson = gsonBuilder.create();
        var locationJson = (JsonObject) arguments.get(0);
        var location = gson.fromJson(locationJson, Location.class);
        var serializeStepsJson = (JsonPrimitive) arguments.get(1);
        var serializeSteps = gson.fromJson(serializeStepsJson, boolean.class);
        var element = getElement(access, location);

        return new InlineClassCommandParams((ClassDeclaration) element, serializeSteps);
    }

    @Override
    protected Object execute(
            InlineClassCommandParams arguments, ILanguageServerAccess access, CommandProgressContext progressContext) {
        progressContext.begin("Compiling class " + arguments.classDeclaration().getName(), "Transforming");

        var classDeclaration = arguments.classDeclaration();
        var targetQualifiedName = oxstsQualifiedNameProvider.getFullyQualifiedNameString(classDeclaration);
        LOGGER.info(
                "LSP inline request for case '{}' (serializeSteps={})",
                targetQualifiedName,
                arguments.serializeSteps());

        var compiler = new SemantifyrCompiler(
                injector, serverSettings.resolveArtifactConfig(), serverSettings.resolveOptimizationConfig());

        semantifyrRequestManager.releaseReadLock();
        try {
            progressContext.checkIsCancelled();
            compiler.compile(classDeclaration, serverSettings.resolveArtifactOutputDirectory());
        } finally {
            semantifyrRequestManager.acquireReadLock();
        }

        return null;
    }
}
