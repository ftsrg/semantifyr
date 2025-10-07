/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

import hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent.PausableRequestManager;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.xtext.ide.ExecutorServiceProvider;
import org.eclipse.xtext.ide.server.*;
import org.eclipse.xtext.ide.server.concurrent.IRequestManager;
import org.eclipse.xtext.resource.IContainer;
import org.eclipse.xtext.resource.IResourceServiceProvider;
import org.eclipse.xtext.resource.ResourceServiceProviderServiceLoader;
import org.eclipse.xtext.resource.containers.ProjectDescriptionBasedContainerManager;

import java.util.concurrent.ExecutorService;

public class OxstsServerModule extends ServerModule {

    @Override
    protected void configure() {
        binder().bind(ExecutorService.class).toProvider(ExecutorServiceProvider.class);

        bind(LanguageServer.class).to(OxstsLanguageServer.class);
        bind(LanguageServerImpl.class).to(OxstsLanguageServer.class);
        bind(IResourceServiceProvider.Registry.class).toProvider(ResourceServiceProviderServiceLoader.class);
        bind(IMultiRootWorkspaceConfigFactory.class).to(MultiRootWorkspaceConfigFactory.class);
        bind(IProjectDescriptionFactory.class).to(DefaultProjectDescriptionFactory.class);
        bind(IContainer.Manager.class).to(ProjectDescriptionBasedContainerManager.class);
        bind(IRequestManager.class).to(PausableRequestManager.class);
    }

}
