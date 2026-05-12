/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import com.google.inject.Inject;
import com.google.inject.Injector;
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler;
import hu.bme.mit.semantifyr.lang.ide.server.ServerSettings;
import hu.bme.mit.semantifyr.lang.ide.server.commands.AbstractCommandHandler;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandProgressContext;
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import java.util.List;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InlineClassCommandHandler
        extends AbstractCommandHandler<InlineClassWireRequest, InlineClassCommandParams> {

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
        return List.of(
                new InlineClassWireRequest(getLocation(arguments.classDeclaration()), arguments.serializeSteps()));
    }

    @Override
    protected Class<InlineClassWireRequest> getRequestType() {
        return InlineClassWireRequest.class;
    }

    @Override
    protected InlineClassCommandParams resolveArgument(
            InlineClassWireRequest request, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        var element = getElement(access, request.location());
        return new InlineClassCommandParams((ClassDeclaration) element, request.serializeSteps());
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
        var outputDirectory = serverSettings.resolveArtifactOutputDirectory(access);

        return performBackgroundWork(() -> {
            progressContext.checkIsCancelled();
            compiler.compile(classDeclaration, outputDirectory);
            return null;
        });
    }
}
