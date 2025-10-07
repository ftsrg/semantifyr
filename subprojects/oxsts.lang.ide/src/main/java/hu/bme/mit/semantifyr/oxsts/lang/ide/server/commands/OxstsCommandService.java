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
    private CompileOxstsCommandHandler compileOxstsCommandHandler;

    @Override
    public List<String> initialize() {
        return List.of(
                compileOxstsCommandHandler.getId()
        );
    }

    @Override
    public Object execute(ExecuteCommandParams params, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        if (! compileOxstsCommandHandler.getId().equals(params.getCommand())) {
            return null;
        }

        return compileOxstsCommandHandler.execute(params, access, cancelIndicator);
    }

}
