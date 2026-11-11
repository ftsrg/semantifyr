/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.ide.server.commands;

import com.google.inject.Inject;
import com.google.inject.Provider;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage;
import hu.bme.mit.semantifyr.frontends.gamma.serialization.GammaToOxstsSerializer;
import hu.bme.mit.semantifyr.lang.ide.server.commands.AbstractCommandHandler;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandProgressContext;
import hu.bme.mit.semantifyr.lang.ide.server.wire.CompiledOxstsResult;
import java.util.List;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompiledOxstsGammaCommandHandler extends AbstractCommandHandler<String, Resource> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompiledOxstsGammaCommandHandler.class);

    @Inject
    private Provider<GammaToOxstsSerializer> serializerProvider;

    @Override
    public String getId() {
        return "gamma.compiled.oxsts";
    }

    @Override
    public String getTitle() {
        return "Show compiled OXSTS";
    }

    @Override
    public List<Object> serializeArguments(Resource arguments) {
        return List.of(arguments.getURI().toString());
    }

    @Override
    protected Class<String> getRequestType() {
        return String.class;
    }

    @Override
    protected Resource resolveArgument(String request, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        return access.doSyncRead(request, ILanguageServerAccess.Context::getResource);
    }

    @Override
    protected Object execute(Resource arguments, ILanguageServerAccess access, CommandProgressContext progressContext) {
        if (!arguments.getErrors().isEmpty()) {
            var error = arguments.getErrors().getFirst();
            return CompiledOxstsResult.errored("The model has a syntax error at line " + error.getLine() + ", column "
                    + error.getColumn() + ": " + error.getMessage());
        }
        if (arguments.getContents().isEmpty()
                || !(arguments.getContents().getFirst() instanceof GammaModelPackage gammaModel)) {
            return CompiledOxstsResult.errored("The document holds no Gamma model");
        }
        try {
            return CompiledOxstsResult.compiled(serializerProvider.get().transformToOxsts(gammaModel));
        } catch (RuntimeException e) {
            LOGGER.info("LSP gamma.compiled.oxsts: {} does not compile", arguments.getURI(), e);
            return CompiledOxstsResult.errored("The model does not compile: " + e.getMessage());
        }
    }
}
