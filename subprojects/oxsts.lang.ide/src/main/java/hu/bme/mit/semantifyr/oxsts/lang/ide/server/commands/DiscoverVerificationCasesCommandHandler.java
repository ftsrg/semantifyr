/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import com.google.gson.JsonPrimitive;
import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinLibraryUtils;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.AnnotationHandler;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class DiscoverVerificationCasesCommandHandler extends AbstractCommandHandler<Resource> {

    @Inject
    private AnnotationHandler annotationHandler;

    @Inject
    private BuiltinSymbolResolver builtinSymbolResolver;

    @Inject
    private BuiltinLibraryUtils builtinLibraryUtils;

    @Override
    public String getId() {
        return "oxsts.case.discover";
    }

    @Override
    public String getTitle() {
        return "Discover";
    }

    @Override
    public List<Object> serializeArguments(Resource arguments) {
        return List.of(arguments.getURI().toString());
    }

    @Override
    protected Resource parseArguments(List<Object> arguments, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        var uriJson = (JsonPrimitive) arguments.getFirst();
        var uri = uriJson.getAsString();

        var resource = access.doRead(uri, ILanguageServerAccess.Context::getResource);

        try {
            return resource.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Object execute(Resource arguments, ILanguageServerAccess access, CommandProgressContext progressContext) {
        if (arguments.getContents().isEmpty()) {
            return List.of();
        }

        if (! (arguments.getContents().getFirst() instanceof OxstsModelPackage oxstsModel)) {
            return List.of();
        }

        var verificationCases = new ArrayList<VerificationCaseSpecification>();

        for (var declaration : oxstsModel.getDeclarations()) {
            if (!(declaration instanceof ClassDeclaration classDeclaration)) {
                continue;
            }

            if (annotationHandler.isVerificationCase(classDeclaration)) {
                verificationCases.add(createCase(classDeclaration));
                verificationCases.add(createCase(classDeclaration));
            }
        }

        return verificationCases;
    }

    private VerificationCaseSpecification createCase(ClassDeclaration classDeclaration) {
        var summary = builtinLibraryUtils.getVerificationCaseSummary(classDeclaration);
        if (summary == null) {
            summary = classDeclaration.getName();
        }
        var id = classDeclaration.getName();
        var location = getLocation(classDeclaration);

        return new VerificationCaseSpecification(id, summary, location);
    }

}
