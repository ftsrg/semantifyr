/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import com.google.gson.JsonPrimitive;
import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.AnnotationHandler;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.util.CancelIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class DiscoverVerificationCasesCommandHandler extends AbstractCommandHandler<OxstsModelPackage> {

    @Inject
    private AnnotationHandler annotationHandler;

    @Override
    public String getId() {
        return "oxsts.case.discover";
    }

    @Override
    public String getTitle() {
        return "Discover";
    }

    @Override
    public List<Object> serializeArguments(OxstsModelPackage arguments) {
        return List.of(arguments.eResource().getURI().toString());
    }

    @Override
    protected OxstsModelPackage parseArguments(List<Object> arguments, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        var uriJson = (JsonPrimitive) arguments.get(0);
        var uri = uriJson.getAsString();

        var classDeclaration = access.doRead(uri, context -> {
            var resource = context.getResource();
            return (OxstsModelPackage) resource.getContents().getFirst();
        });

        try {
            return classDeclaration.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Object execute(OxstsModelPackage arguments, ILanguageServerAccess access, CommandProgressContext progressContext) {
        var verificationCases = new ArrayList<VerificationCaseSpecification>();

        for (var declaration : arguments.getDeclarations()) {
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
        var id = classDeclaration.getName();
        var classNode = NodeModelUtils.getNode(classDeclaration);
        var start = new Position(classNode.getStartLine() - 1, 0);
        var range = new Range(start, start);

        return new VerificationCaseSpecification(id, id, range);
    }

}
