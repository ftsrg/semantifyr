/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent.WorkManager;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RedefinableDeclaration;
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScope;
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScopeContext;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.ide.server.DocumentExtensions;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.resource.EObjectAtOffsetHelper;
import org.eclipse.xtext.resource.IResourceServiceProvider;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class AbstractCommandHandler<T> implements CommandHandler {

    @Inject
    protected WorkManager workManager;

    @Inject
    @Named("compilationScope")
    protected CompilationScope compilationScope;

    @Inject
    private IResourceServiceProvider.Registry resourceServiceProviderRegistry;

    @Inject
    private EObjectAtOffsetHelper eObjectAtOffsetHelper;

    @Override
    public Object execute(ExecuteCommandParams params, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        if (! params.getCommand().equals(getId())) {
            throw new IllegalArgumentException("This handler is not for the given command!");
        }

        var argument = parseArguments(params.getArguments(), access, cancelIndicator);

        var progressContext = new CommandProgressContext(workManager);

        try {
            return execute(argument, access, progressContext);
        } catch (Exception e) {
            progressContext.end("Failure!");
            throw e;
        }
    }

    public abstract List<Object> serializeArguments(T arguments);

    protected abstract T parseArguments(List<Object> arguments, ILanguageServerAccess access, CancelIndicator cancelIndicator);

    protected abstract Object execute(T argument, ILanguageServerAccess access, CommandProgressContext progressContext);

    /**
     * Runs {@code runnable} in a new compilation scope.
     */
    protected void compilationScopeRunnable(Runnable runnable) {
        var compilationScopeContext = new CompilationScopeContext();
        compilationScope.enter(compilationScopeContext);
        try {
            runnable.run();
        } finally {
            compilationScope.exit();
        }
    }

    protected EObject getElement(final ILanguageServerAccess.Context context, Position position) {
        var offset = context.getDocument().getOffSet(position);
        return eObjectAtOffsetHelper.getElementWithNameAt((XtextResource) context.getResource(), offset);
    }

    protected CompletableFuture<EObject> getElement(final ILanguageServerAccess access, Location location) {
        return access.doRead(location.getUri(), context -> getElement(context, location.getRange().getStart()));
    }

    protected Location getLocation(final EObject eObject) {
        return getDocumentExtensions(eObject.eResource().getURI()).newLocation(eObject);
    }

    protected DocumentExtensions getDocumentExtensions(final URI targetURI) {
        return resourceServiceProviderRegistry.getResourceServiceProvider(targetURI.trimFragment())
                .get(DocumentExtensions.class);
    }

}
