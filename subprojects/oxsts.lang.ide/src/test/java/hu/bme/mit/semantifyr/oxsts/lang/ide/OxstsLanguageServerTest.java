/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide;

import com.google.inject.Module;
import hu.bme.mit.semantifyr.oxsts.lang.OxstsRuntimeModule;
import hu.bme.mit.semantifyr.oxsts.lang.ide.client.SemantifyrLanguageClient;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.OxstsServerModule;
import java.nio.file.Path;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.xtext.ide.server.concurrent.IRequestManager;
import org.eclipse.xtext.testing.AbstractLanguageServerTest;
import org.eclipse.xtext.util.Modules2;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

public abstract class OxstsLanguageServerTest extends AbstractLanguageServerTest {

    private static final String MODEL_FILE_NAME = "snippet.oxsts";

    private static final String LIBRARY_DIRECTORY_NAME = "Library";

    @TempDir
    Path workspaceDirectory;

    protected OxstsLanguageServerTest() {
        super("oxsts");
    }

    @BeforeAll
    static void registerOxsts() {
        new OxstsIdeSetup().createInjectorAndDoEMFRegistration();
    }

    @BeforeEach
    void useWorkspaceDirectory() {
        root = workspaceDirectory.toFile();
    }

    @Override
    protected Module getServerModule() {
        return Modules2.mixin(new OxstsServerModule(), new OxstsRuntimeModule(), new OxstsIdeModule(), binder -> {
            // Answers each request on the test thread before the call returns.
            binder.bind(IRequestManager.class).to(DirectRequestManager.class);
        });
    }

    @Override
    protected Class<? extends LanguageClient> getLanguageClientClass() {
        return SemantifyrLanguageClient.class;
    }

    // The server indexes the workspace when openModel initializes it, so library files are added before.
    protected String addLibraryFile(String fileName, String text) {
        return writeFile(LIBRARY_DIRECTORY_NAME + "/" + fileName, text);
    }

    protected TextDocumentIdentifier openModel(String text) {
        var uri = writeFile(MODEL_FILE_NAME, text);
        initialize();
        open(uri, text);
        return new TextDocumentIdentifier(uri);
    }
}
