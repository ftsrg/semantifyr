/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.ide.server.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandHandler;
import hu.bme.mit.semantifyr.lang.ide.server.commands.SemantifyrCommandService;
import java.util.List;

@Singleton
public class GammaCommandService extends SemantifyrCommandService {

    @Inject
    private DiscoverVerificationCasesCommandHandler discoverVerificationCasesCommandHandler;

    @Inject
    private VerifyGammaCaseCommandHandler verifyGammaCaseCommandHandler;

    @Inject
    private CompileGammaCommandHandler compileGammaCommandHandler;

    private List<CommandHandler> commandHandlers;

    @Override
    protected List<CommandHandler> getCommandHandlers() {
        if (commandHandlers == null) {
            commandHandlers = List.of(
                    discoverVerificationCasesCommandHandler, verifyGammaCaseCommandHandler, compileGammaCommandHandler);
        }
        return commandHandlers;
    }
}
