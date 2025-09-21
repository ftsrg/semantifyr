/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.commands;

import com.google.gson.JsonPrimitive;
import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import hu.bme.mit.semantifyr.semantics.transformation.OxstsToXstsTransformer;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.ide.server.commands.IExecutableCommandService;
import org.eclipse.xtext.util.CancelIndicator;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class OxstsCommandService implements IExecutableCommandService {

    @Inject
    protected OxstsToXstsTransformer xstsTransformer;

    @Override
    public List<String> initialize() {
        return List.of(
                "oxsts.class.compile"
        );
    }

    @Override
    public Object execute(ExecuteCommandParams params, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        if (! params.getCommand().equals("oxsts.class.compile")) {
            return null;
        }

        var uriJson = (JsonPrimitive) params.getArguments().get(0);
        var classNameJson = (JsonPrimitive) params.getArguments().get(1);

        var uri = uriJson.getAsString();
        var className = classNameJson.getAsString();

        var classDeclaration = access.doRead(uri, context -> {
            var resource = context.getResource();
            var oxstsPackage = (OxstsModelPackage) resource.getContents().getFirst();
            return (ClassDeclaration) oxstsPackage.getDeclarations().stream().filter(c -> className.equals(c.getName())).findFirst().get();
        });

        try {
            xstsTransformer.transform(classDeclaration.get(), true);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

        params.setWorkDoneToken(10);

        return null;
    }

}
