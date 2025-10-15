/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent.SemantifyrRequestManager;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent.WorkManager;
import org.eclipse.lsp4j.*;
import org.eclipse.xtext.ide.server.BuildManager;
import org.eclipse.xtext.ide.server.LanguageServerImpl;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Singleton
public class OxstsLanguageServer extends LanguageServerImpl {

    @Inject
    protected WorkManager workManager;

    @Override
    public void cancelProgress(WorkDoneProgressCancelParams params) {
        workManager.cancelProgress(params);
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        workManager.initialize(getLanguageClient());
        return super.initialize(params);
    }

    @Override
    protected void runBuildable(Supplier<? extends BuildManager.Buildable> newBuildable) {
        getRequestManager().runInterruptableWrite(newBuildable::get, (cancelIndicator, buildable) -> buildable.build(cancelIndicator));
    }

    @Override
    public SemantifyrRequestManager getRequestManager() {
        return (SemantifyrRequestManager) super.getRequestManager();
    }

}
