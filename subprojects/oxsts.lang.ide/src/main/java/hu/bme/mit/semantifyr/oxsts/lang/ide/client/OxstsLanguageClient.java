/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.client;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.concurrent.CompletableFuture;

public interface OxstsLanguageClient extends LanguageClient {

    /**
     * The {@code workspace/navigateTo} request is sent from the server to the client to navigate to position.
     */
    @JsonNotification("workspace/navigateTo")
    default CompletableFuture<Void> navigateTo(NavigateToParams params) {
        throw new UnsupportedOperationException();
    }

}
