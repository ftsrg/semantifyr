/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import hu.bme.mit.semantifyr.semantics.progress.ProgressContext;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.backends.theta.summary.ThetaSummaryGenerator;
import hu.bme.mit.semantifyr.semantics.StandaloneOxstsSemanticsRuntimeModule;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.ServerSettings;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.semantics.reader.SemantifyrLoader;
import hu.bme.mit.semantifyr.semantics.verification.SemantifyrCompiler;
import org.eclipse.lsp4j.Location;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Singleton
public class GenerateTracesOxstsCommandHandler extends AbstractCommandHandler<ClassDeclaration> {

    private static final Logger LOG = LoggerFactory.getLogger(GenerateTracesOxstsCommandHandler.class);

    @Inject
    protected SemantifyrLoader semantifyrLoader;

    @Inject
    protected ServerSettings serverSettings;

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
        LOG.info("LSP tracegen request for case '{}'", arguments.getName());

        var context = semantifyrLoader.fromResourceSet(arguments.eResource().getResourceSet());

        semantifyrRequestManager.releaseReadLock();
        try (SemantifyrCompiler compiler = SemantifyrCompiler.builder()
                .context(context)
                .artifacts(serverSettings.resolveArtifactConfig())
                .optimization(serverSettings.resolveOptimizationConfig())
                .progress(progressContext)
                .build()) {
            compiler.withElementBlocking(arguments, clonedClass -> {
                progressContext.checkIsCancelled();
                StandaloneOxstsSemanticsRuntimeModule.INSTANCE
                        .getInjector()
                        .getInstance(ThetaSummaryGenerator.class)
                        .createSummary(progressContext, clonedClass);
                return null;
            });
        } finally {
            semantifyrRequestManager.acquireReadLock();
        }

        return null;
    }

}
