/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.ide.server.commands;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.frontends.gamma.GammaVerificationCase;
import hu.bme.mit.semantifyr.frontends.gamma.discovery.GammaVerificationCaseDiscoverer;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage;
import hu.bme.mit.semantifyr.lang.ide.server.commands.AbstractCommandHandler;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandProgressContext;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseSpecification;
import java.util.List;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;

public class DiscoverVerificationCasesCommandHandler extends AbstractCommandHandler<String, Resource> {

    @Inject
    private GammaVerificationCaseDiscoverer discoverer;

    @Override
    public String getId() {
        return "gamma.case.discover";
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

        if (!(arguments.getContents().getFirst() instanceof GammaModelPackage gammaModel)) {
            return List.of();
        }

        return discoverer.discover(gammaModel).stream().map(this::createCase).toList();
    }

    private VerificationCaseSpecification createCase(GammaVerificationCase gammaCase) {
        var id = gammaCase.getQualifiedName();
        var declaration = gammaCase.getDeclaration();
        var name = declaration.getName();
        var label = name != null ? name : id;
        var location = getLocation(declaration);
        return new VerificationCaseSpecification(id, label, location);
    }
}
