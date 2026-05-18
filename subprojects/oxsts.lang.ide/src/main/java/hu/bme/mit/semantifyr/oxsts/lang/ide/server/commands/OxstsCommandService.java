/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandHandler;
import hu.bme.mit.semantifyr.lang.ide.server.commands.SemantifyrCommandService;
import java.util.List;

@Singleton
public class OxstsCommandService extends SemantifyrCommandService {

    @Inject
    private InlineClassCommandHandler inlineClassCommandHandler;

    @Inject
    private VerifyClassCommandHandler verifyClassCommandHandler;

    @Inject
    private VerifyInlinedOxstsCommandHandler verifyInlinedOxstsCommandHandler;

    @Inject
    private ValidateWitnessClassCommandHandler validateWitnessClassCommandHandler;

    @Inject
    private CompileInlinedOxstsCommandHandler compileInlinedOxstsCommandHandler;

    @Inject
    private DiscoverVerificationCasesCommandHandler discoverVerificationCasesCommandHandler;

    @Inject
    private NavigateToRedefinedCommandHandler navigateToRedefinedCommandHandler;

    @Inject
    private NavigateToRedefinersCommandHandler navigateToRedefinersCommandHandler;

    private List<CommandHandler> commandHandlers;

    @Override
    protected List<CommandHandler> getCommandHandlers() {
        if (commandHandlers == null) {
            commandHandlers = List.of(
                    inlineClassCommandHandler,
                    compileInlinedOxstsCommandHandler,
                    verifyClassCommandHandler,
                    verifyInlinedOxstsCommandHandler,
                    validateWitnessClassCommandHandler,
                    discoverVerificationCasesCommandHandler,
                    navigateToRedefinedCommandHandler,
                    navigateToRedefinersCommandHandler);
        }
        return commandHandlers;
    }
}
