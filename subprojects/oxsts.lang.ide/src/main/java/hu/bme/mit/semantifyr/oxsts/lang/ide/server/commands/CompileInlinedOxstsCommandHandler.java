/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import com.google.gson.JsonPrimitive;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.backends.theta.verification.transformation.xsts.OxstsTransformer;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Singleton
public class CompileInlinedOxstsCommandHandler extends AbstractCommandHandler<InlinedOxsts> {

    @Inject
    protected Provider<OxstsTransformer> oxstsTransformerProvider;

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
        return List.of(arguments.eResource().getURI().toString());
    }

    @Override
    protected InlinedOxsts parseArguments(List<Object> arguments, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        var uriJson = (JsonPrimitive) arguments.get(0);

        var uri = uriJson.getAsString();

        var inlinedOxsts = access.doRead(uri, context -> {
            var resource = context.getResource();
            return(InlinedOxsts) resource.getContents().getFirst();
        });

        try {
            return inlinedOxsts.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Object execute(InlinedOxsts arguments, ILanguageServerAccess access, CommandProgressContext progressContext) {
        compilationScopeRunnable(() -> {
            var xsts = oxstsTransformerProvider.get().transform(arguments, false);

            try {
                xsts.eResource().save(null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        return null;
    }

}
