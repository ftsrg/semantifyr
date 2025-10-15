/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent.SemantifyrRequestManager;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent.WorkManager;
import hu.bme.mit.semantifyr.semantics.transformation.CompilationScopeManager;
import hu.bme.mit.semantifyr.semantics.transformation.EObjectRunnable;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.xtext.ide.server.DocumentExtensions;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.resource.EObjectAtOffsetHelper;
import org.eclipse.xtext.resource.IResourceServiceProvider;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;

import java.util.List;

public abstract class AbstractCommandHandler<T> implements CommandHandler {

    @Inject
    protected WorkManager workManager;

    @Inject
    protected SemantifyrRequestManager semantifyrRequestManager;

    @Inject
    protected CompilationScopeManager compilationScopeManager;

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

        var progressContext = new CommandProgressContext(workManager, cancelIndicator);

        try {
            return execute(argument, access, progressContext);
        } finally {
            progressContext.end();
        }
    }

    public abstract List<Object> serializeArguments(T arguments);

    protected abstract T parseArguments(List<Object> arguments, ILanguageServerAccess access, CancelIndicator cancelIndicator);

    protected abstract Object execute(T argument, ILanguageServerAccess access, CommandProgressContext progressContext);

    protected <TArg extends EObject> void runLongRunningInCompilationScope(TArg eObject, EObjectRunnable<TArg> runnable) {
        compilationScopeManager.runInCompilationScope(eObject, (copied) -> {
            semantifyrRequestManager.releaseReadLock();
            try {
                runnable.run(copied);
            } finally {
                semantifyrRequestManager.acquireReadLock();
            }
        });
    }

    protected EObject getElement(final ILanguageServerAccess.Context context, Position position) {
        var offset = context.getDocument().getOffSet(position);
        return eObjectAtOffsetHelper.getElementWithNameAt((XtextResource) context.getResource(), offset);
    }

    protected EObject getElement(final ILanguageServerAccess access, Location location) {
        return access.doSyncRead(location.getUri(), context -> getElement(context, location.getRange().getStart()));
    }

    protected Location getLocation(final EObject eObject) {
        return getDocumentExtensions(eObject.eResource().getURI()).newLocation(eObject);
    }

    protected DocumentExtensions getDocumentExtensions(final URI targetURI) {
        return resourceServiceProviderRegistry.getResourceServiceProvider(targetURI.trimFragment())
                .get(DocumentExtensions.class);
    }

}
