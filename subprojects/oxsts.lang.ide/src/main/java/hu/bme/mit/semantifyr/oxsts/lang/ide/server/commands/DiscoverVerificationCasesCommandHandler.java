/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.lang.ide.server.commands.AbstractCommandHandler;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandProgressContext;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseSpecification;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltInLibraryUtils;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler;
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;

public class DiscoverVerificationCasesCommandHandler extends AbstractCommandHandler<String, Resource> {

    @Inject
    private BuiltinAnnotationHandler builtinAnnotationHandler;

    @Inject
    private BuiltInLibraryUtils builtInLibraryUtils;

    @Inject
    private OxstsQualifiedNameProvider oxstsQualifiedNameProvider;

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
    protected Class<String> getRequestType() {
        return String.class;
    }

    @Override
    protected Resource resolveArgument(String request, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        return access.doSyncRead(request, ILanguageServerAccess.Context::getResource);
    }

    @Override
    protected Object execute(Resource arguments, ILanguageServerAccess access, CommandProgressContext progressContext) {
        if (arguments.getContents().isEmpty()) {
            return List.of();
        }

        if (!(arguments.getContents().getFirst() instanceof OxstsModelPackage oxstsModel)) {
            return List.of();
        }

        var verificationCases = new ArrayList<VerificationCaseSpecification>();
        builtInLibraryUtils
                .streamTestCases(oxstsModel)
                .forEach(testCase -> verificationCases.add(createCase(testCase)));

        return verificationCases;
    }

    private VerificationCaseSpecification createCase(ClassDeclaration classDeclaration) {
        var id = oxstsQualifiedNameProvider.getFullyQualifiedNameString(classDeclaration);
        var summary = builtinAnnotationHandler.getVerificationCaseSummary(classDeclaration);
        if (summary == null) {
            summary = id;
        }
        var location = getLocation(classDeclaration);

        return new VerificationCaseSpecification(id, summary, location);
    }
}
