/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.commands;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import hu.bme.mit.semantifyr.lang.ide.server.concurrent.WorkManager;
import java.util.concurrent.CancellationException;
import org.eclipse.lsp4j.WorkDoneProgressCancelParams;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage;
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.xtext.util.CancelIndicator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CommandProgressContextTest {

    @Test
    void aProgressCancelTheClientSendsStopsEveryLaterCheckOfTheCommand() {
        var client = mock(LanguageClient.class);
        var workManager = new WorkManager();
        workManager.initialize(client);
        var progressContext = new CommandProgressContext(workManager, CancelIndicator.NullImpl);
        progressContext.begin("Validating witness", "Initializing");
        var created = ArgumentCaptor.forClass(WorkDoneProgressCreateParams.class);
        verify(client).createProgress(created.capture());
        var token = created.getValue().getToken().getLeft();
        var jsonHandler = new MessageJsonHandler(ServiceEndpoints.getSupportedMethods(LanguageServer.class));
        var cancel = (NotificationMessage) jsonHandler.parseMessage("""
                {"jsonrpc": "2.0", "method": "window/workDoneProgress/cancel", "params": {"token": "%s"}}
                """.formatted(token));

        workManager.cancelProgress((WorkDoneProgressCancelParams) cancel.getParams());

        assertThatThrownBy(progressContext::checkIsCancelled).isInstanceOf(CancellationException.class);
        assertThatThrownBy(progressContext::checkIsCancelled).isInstanceOf(CancellationException.class);
    }
}
