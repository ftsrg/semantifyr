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
import hu.bme.mit.semantifyr.backends.theta.transformation.xsts.OxstsTransformer;
import hu.bme.mit.semantifyr.semantics.StandaloneOxstsSemanticsRuntimeModule;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.ServerSettings;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts;
import hu.bme.mit.semantifyr.semantics.reader.SemantifyrLoader;
import hu.bme.mit.semantifyr.semantics.verification.SemantifyrCompiler;
import org.eclipse.lsp4j.Location;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

@Singleton
public class CompileInlinedOxstsCommandHandler extends AbstractCommandHandler<InlinedOxsts> {

    private static final Logger LOG = LoggerFactory.getLogger(CompileInlinedOxstsCommandHandler.class);

    @Inject
    protected SemantifyrLoader semantifyrLoader;

    @Inject
    protected ServerSettings serverSettings;

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

        return (InlinedOxsts) element;
    }

    @Override
    protected Object execute(InlinedOxsts arguments, ILanguageServerAccess access, CommandProgressContext progressContext) {
        LOG.info("LSP compile-inlined request");
        var context = semantifyrLoader.fromResourceSet(arguments.eResource().getResourceSet());

        semantifyrRequestManager.releaseReadLock();
        try (SemantifyrCompiler compiler = SemantifyrCompiler.builder()
                .context(context)
                .artifacts(serverSettings.resolveArtifactConfig())
                .optimization(serverSettings.resolveOptimizationConfig())
                .progress(progressContext)
                .build()) {
            compiler.withElementBlocking(arguments, clonedInlined -> {
                var xsts = StandaloneOxstsSemanticsRuntimeModule.INSTANCE
                        .getInjector()
                        .getInstance(OxstsTransformer.class)
                        .transform(clonedInlined);
                try {
                    xsts.eResource().save(null);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
        } finally {
            semantifyrRequestManager.acquireReadLock();
        }

        return null;
    }

}
