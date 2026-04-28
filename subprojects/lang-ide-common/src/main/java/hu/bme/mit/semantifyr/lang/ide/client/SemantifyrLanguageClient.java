/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.client;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;

public interface SemantifyrLanguageClient extends LanguageClient {

    /**
     * The {@code workspace/navigateTo} request is sent from the server to the client to navigate to
     * position.
     */
    @JsonNotification("workspace/navigateTo")
    default CompletableFuture<Void> navigateTo(NavigateToParams params) {
        throw new UnsupportedOperationException();
    }
}
