/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import hu.bme.mit.semantifyr.semantics.progress.ProgressContext;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.ServerSettings;
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.semantics.reader.SemantifyrLoader;
import hu.bme.mit.semantifyr.semantics.verification.CaseFilter;
import hu.bme.mit.semantifyr.semantics.verification.SemantifyrCompiler;
import hu.bme.mit.semantifyr.semantics.verification.VerificationCase;
import org.eclipse.lsp4j.Location;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Singleton
public class InlineOxstsCommandHandler extends AbstractCommandHandler<InlineOxstsCommandParams> {

    private static final Logger LOG = LoggerFactory.getLogger(InlineOxstsCommandHandler.class);

    @Inject
    protected OxstsQualifiedNameProvider oxstsQualifiedNameProvider;

    @Inject
    protected SemantifyrLoader semantifyrLoader;

    @Inject
    protected ServerSettings serverSettings;

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

        var targetFqn = oxstsQualifiedNameProvider.getFullyQualifiedNameString(arguments.classDeclaration());
        LOG.info("LSP inline request for case '{}' (serializeSteps={})", targetFqn, arguments.serializeSteps());
        var context = semantifyrLoader.fromResourceSet(arguments.classDeclaration().eResource().getResourceSet());

        // serializeSteps is accepted at the LSP boundary but not yet honored;
        // re-attach when ArtifactConfig.compilationSteps is wired through the builder.

        semantifyrRequestManager.releaseReadLock();
        try (SemantifyrCompiler compiler = SemantifyrCompiler.builder()
                .context(context)
                .artifacts(serverSettings.resolveArtifactConfig())
                .optimization(serverSettings.resolveOptimizationConfig())
                .progress(progressContext)
                .build()) {
            progressContext.checkIsCancelled();

            VerificationCase match = compiler.verificationCases(CaseFilter.All.INSTANCE).stream()
                    .filter(c -> targetFqn.equals(c.getFqn()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No verification case found for fqn '" + targetFqn + "'"));

            compiler.inlineBlocking(match);
        } finally {
            semantifyrRequestManager.acquireReadLock();
        }

        return null;
    }

}
