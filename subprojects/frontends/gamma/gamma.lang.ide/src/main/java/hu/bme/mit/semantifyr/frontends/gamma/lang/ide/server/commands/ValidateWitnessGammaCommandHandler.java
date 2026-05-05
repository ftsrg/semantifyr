/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.ide.server.commands;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.lang.ide.server.ServerSettings;
import hu.bme.mit.semantifyr.lang.ide.server.commands.AbstractCommandHandler;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandProgressContext;
import java.util.List;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidateWitnessGammaCommandHandler extends AbstractCommandHandler<ValidateWitnessCommandParams> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidateWitnessGammaCommandHandler.class);

    @Inject
    protected ServerSettings serverSettings;

    @Override
    public String getId() {
        return "gamma.witness.validate";
    }

    @Override
    public String getTitle() {
        return "Validate witness";
    }

    @Override
    public List<Object> serializeArguments(ValidateWitnessCommandParams arguments) {
        throw new IllegalStateException("Not yet implemented");
    }

    @Override
    protected ValidateWitnessCommandParams parseArguments(
            List<Object> arguments, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        throw new IllegalStateException("Not yet implemented");
    }

    @Override
    protected Object execute(
            ValidateWitnessCommandParams arguments,
            ILanguageServerAccess access,
            CommandProgressContext progressContext) {

        throw new IllegalStateException("Not yet implemented");
    }
}
