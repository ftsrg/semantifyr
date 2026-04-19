/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.ServerSettings;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts;
import org.eclipse.lsp4j.Location;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Singleton
public class CompileInlinedOxstsCommandHandler extends AbstractCommandHandler<InlinedOxsts> {

    private static final Logger LOG = LoggerFactory.getLogger(CompileInlinedOxstsCommandHandler.class);

    @Inject
    protected ServerSettings serverSettings;

    @Inject
    protected Injector injector;

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

        semantifyrRequestManager.releaseReadLock();
        try (SemantifyrCompiler compiler = new SemantifyrCompiler(
                injector,
                serverSettings.resolveArtifactConfig(),
                serverSettings.resolveOptimizationConfig())) {
            compiler.compile(arguments);
        } finally {
            semantifyrRequestManager.acquireReadLock();
        }

        return null;
    }

}
