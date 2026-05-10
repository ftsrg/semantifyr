/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.commands;

import com.google.gson.JsonElement;
import com.google.inject.Inject;
import com.google.inject.Provider;
import hu.bme.mit.semantifyr.lang.ide.server.concurrent.SemantifyrRequestManager;
import hu.bme.mit.semantifyr.lang.ide.server.concurrent.WorkManager;
import java.util.List;
import java.util.function.Supplier;
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

public abstract class AbstractCommandHandler<TRequest, TArgument> implements CommandHandler {

    @Inject
    private Provider<WorkManager> workManagerProvider;

    @Inject
    private Provider<SemantifyrRequestManager> requestManagerProvider;

    @Inject
    private IResourceServiceProvider.Registry resourceServiceProviderRegistry;

    @Inject
    private EObjectAtOffsetHelper eObjectAtOffsetHelper;

    @Override
    public Object execute(ExecuteCommandParams params, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        if (!params.getCommand().equals(getId())) {
            throw new IllegalArgumentException("This handler is not for the given command!");
        }

        var requestJson = (JsonElement) params.getArguments().get(0);
        var request = CommandGson.INSTANCE.fromJson(requestJson, getRequestType());
        var argument = resolveArgument(request, access, cancelIndicator);

        var progressContext = new CommandProgressContext(workManagerProvider.get(), cancelIndicator);

        try {
            return execute(argument, access, progressContext);
        } finally {
            progressContext.end();
        }
    }

    protected <T> T performBackgroundWork(Supplier<T> work) {
        return requestManagerProvider.get().performBackgroundWork(work);
    }

    public abstract List<Object> serializeArguments(TArgument arguments);

    protected abstract Class<TRequest> getRequestType();

    protected abstract TArgument resolveArgument(
            TRequest request, ILanguageServerAccess access, CancelIndicator cancelIndicator);

    protected abstract Object execute(
            TArgument argument, ILanguageServerAccess access, CommandProgressContext progressContext);

    protected EObject getElement(final ILanguageServerAccess.Context context, Position position) {
        var offset = context.getDocument().getOffSet(position);
        return eObjectAtOffsetHelper.resolveElementAt((XtextResource) context.getResource(), offset);
    }

    protected EObject getElement(final ILanguageServerAccess access, Location location) {
        return access.doSyncRead(
                location.getUri(),
                context -> getElement(context, location.getRange().getStart()));
    }

    protected Location getLocation(final EObject eObject) {
        return getDocumentExtensions(eObject.eResource().getURI()).newLocation(eObject);
    }

    protected DocumentExtensions getDocumentExtensions(final URI targetURI) {
        return resourceServiceProviderRegistry
                .getResourceServiceProvider(targetURI.trimFragment())
                .get(DocumentExtensions.class);
    }
}
