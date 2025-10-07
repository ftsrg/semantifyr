/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.concurrent;

import org.apache.log4j.Logger;
import org.eclipse.xtext.ide.server.concurrent.AbstractRequest;
import org.eclipse.xtext.ide.server.concurrent.AbstractRequestManager;
import org.eclipse.xtext.xbase.lib.Functions;

public class DeferredRequest<V> extends AbstractRequest<V> {
    private static final Logger LOG = Logger.getLogger(DeferredRequest.class);

    private final Functions.Function0<AbstractRequest<V>> deferred;

    protected DeferredRequest(AbstractRequestManager requestManager, Functions.Function0<AbstractRequest<V>> deferred) {
        super(requestManager);
        this.deferred = deferred;
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }

    public AbstractRequest<V> getDeferred() {
        var request = deferred.apply();
        request.get().handle((v, t) -> {
            if (t != null) {
                logAndCompleteExceptionally(t);
            } else {
                complete(v);
            }

            return null;
        });
        return request;
    }

    @Override
    public void run() {
        throw new IllegalStateException("This class is not supposed to be run!");
    }

}
