/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.lsp4j.*;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.ide.server.commands.IExecutableCommandService;
import org.eclipse.xtext.util.CancelIndicator;

import java.util.List;

@Singleton
public class OxstsCommandService implements IExecutableCommandService {

    @Inject
    private InlineOxstsCommandHandler inlineOxstsCommandHandler;

    @Inject
    private VerifyOxstsCommandHandler verifyOxstsCommandHandler;

    @Inject
    private CompileInlinedOxstsCommandHandler compileInlinedOxstsCommandHandler;

    @Inject
    private DiscoverVerificationCasesCommandHandler discoverVerificationCasesCommandHandler;

    @Inject
    private NavigateToRedefinedCommandHandler navigateToRedefinedCommandHandler;

    private List<CommandHandler> commandHandlers;

    protected List<CommandHandler> getCommandHandlers() {
        if (commandHandlers == null) {
            commandHandlers = List.of(
                    inlineOxstsCommandHandler,
                    compileInlinedOxstsCommandHandler,
                    verifyOxstsCommandHandler,
                    discoverVerificationCasesCommandHandler,
                    navigateToRedefinedCommandHandler
            );
        }
        return commandHandlers;
    }

    @Override
    public List<String> initialize() {
        return getCommandHandlers().stream().map(CommandHandler::getId).toList();
    }

    @Override
    public Object execute(ExecuteCommandParams params, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        var commandHandler = getCommandHandlers().stream().filter(c -> params.getCommand().equals(c.getId())).findFirst();

        return commandHandler.map(handler -> handler.execute(params, access, cancelIndicator)).orElse(null);
    }

}
