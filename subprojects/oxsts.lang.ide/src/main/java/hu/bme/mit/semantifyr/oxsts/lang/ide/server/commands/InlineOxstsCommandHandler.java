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
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent.PausableRequestManager;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import hu.bme.mit.semantifyr.semantics.transformation.OxstsToXstsTransformer;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Singleton
public class InlineOxstsCommandHandler extends AbstractCommandHandler<ClassDeclaration> {

    @Inject
    protected Provider<OxstsToXstsTransformer> xstsTransformerProvider;

    @Inject
    protected PausableRequestManager pausableRequestManager;

    @Override
    public String getId() {
        return "oxsts.class.inline";
    }

    @Override
    public String getTitle() {
        return "Inline";
    }

    @Override
    public List<Object> serializeArguments(ClassDeclaration arguments) {
        return List.of(arguments.eResource().getURI().toString(), arguments.getName());
    }

    @Override
    protected ClassDeclaration parseArguments(List<Object> arguments, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        var uriJson = (JsonPrimitive) arguments.get(0);
        var classNameJson = (JsonPrimitive) arguments.get(1);

        var uri = uriJson.getAsString();
        var className = classNameJson.getAsString();

        var classDeclaration = access.doRead(uri, context -> {
            var resource = context.getResource();
            var oxstsPackage = (OxstsModelPackage) resource.getContents().getFirst();
            return (ClassDeclaration) oxstsPackage.getDeclarations().stream().filter(c -> className.equals(c.getName())).findFirst().get();
        });

        try {
            return classDeclaration.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Object execute(ClassDeclaration arguments, ILanguageServerAccess access, CommandProgressContext progressContext) {
        progressContext.begin("Compiling class " + arguments.getName(), "Initializing");

        pausableRequestManager.pause();

        try {
            compilationScopeRunnable(() -> {
                progressContext.checkIsCancelled();

                xstsTransformerProvider.get().transform(progressContext, arguments);
            });

            progressContext.end("Compilation done!");
        } finally {
            pausableRequestManager.resume();
        }

        return null;
    }

}
