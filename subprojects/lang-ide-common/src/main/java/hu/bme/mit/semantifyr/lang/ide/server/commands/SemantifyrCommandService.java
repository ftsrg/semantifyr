/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.commands;

import java.util.List;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.ide.server.commands.IExecutableCommandService;
import org.eclipse.xtext.util.CancelIndicator;

public abstract class SemantifyrCommandService implements IExecutableCommandService {

    protected abstract List<CommandHandler> getCommandHandlers();

    @Override
    public List<String> initialize() {
        return getCommandHandlers().stream().map(CommandHandler::getId).toList();
    }

    @Override
    public Object execute(ExecuteCommandParams params, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        var commandHandler = getCommandHandlers().stream()
                .filter(c -> params.getCommand().equals(c.getId()))
                .findFirst();

        return commandHandler
                .map(handler -> handler.execute(params, access, cancelIndicator))
                .orElse(null);
    }
}
