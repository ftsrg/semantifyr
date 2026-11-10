/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.inject.Injector;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandProgressContext;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationStatus;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsIdeSetup;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import org.eclipse.emf.common.util.URI;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.ide.server.WorkspaceManager;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnsavedEditsCommandHandlerTest {

    private static final String SAVED_MODEL = """
            package semantifyr::verification::buffer

            @VerificationCase
            class Decided {
                prop {
                    return AG true
                }
            }
            """;

    private static final String EDITED_MODEL = """
            package semantifyr::verification::buffer

            @VerificationCase
            class Decided {
                prop {
                    return AG true
                }
            }

            @VerificationCase
            class OnlyInBuffer {
                prop {
                    return AG false
                }
            }
            """;

    @TempDir
    private Path workspace;

    private Injector injector;

    private ClassDeclaration onlyInBuffer;

    @BeforeEach
    void setUp() throws Exception {
        injector = new OxstsIdeSetup().createInjectorAndDoEMFRegistration();
        Files.writeString(workspace.resolve("decided.oxsts"), SAVED_MODEL);
        var workspaceManager = injector.getInstance(WorkspaceManager.class);
        workspaceManager.initialize(
                List.of(new WorkspaceFolder(workspace.toUri().toString(), "workspace")),
                (uri, issues) -> {},
                CancelIndicator.NullImpl);
        var modelUri = URI.createURI(workspace.resolve("decided.oxsts").toUri().toString());
        workspaceManager.didOpen(modelUri, 1, EDITED_MODEL).build(CancelIndicator.NullImpl);
        XtextResource resource = workspaceManager.doRead(modelUri, (document, xtextResource) -> xtextResource);
        onlyInBuffer =
                EcoreUtil2.getAllContentsOfType(resource.getContents().getFirst(), ClassDeclaration.class).stream()
                        .filter(classDeclaration -> {
                            return "OnlyInBuffer".equals(classDeclaration.getName());
                        })
                        .findFirst()
                        .orElseThrow();
    }

    private <T> T inject(T handler) {
        injector.injectMembers(handler);
        return handler;
    }

    @Test
    void verificationCompilesTheUnsavedBuffer() {
        var handler = inject(new VerifyClassCommandHandler() {
            @Override
            protected <T> T performBackgroundWork(Supplier<T> work) {
                return work.get();
            }
        });

        var result = (VerificationCaseResult) handler.execute(
                new VerifyClassCommandParams(onlyInBuffer, null),
                mock(ILanguageServerAccess.class),
                mock(CommandProgressContext.class));

        assertThat(result.status()).isEqualTo(VerificationStatus.FAILED);
        assertThat(result.backendId()).isEqualTo("trivial");
    }

    @Test
    void inliningCompilesTheUnsavedBuffer() throws Exception {
        var handler = inject(new InlineClassCommandHandler() {
            @Override
            protected <T> T performBackgroundWork(Supplier<T> work) {
                return work.get();
            }
        });

        var result = handler.execute(
                new InlineClassCommandParams(onlyInBuffer, false),
                mock(ILanguageServerAccess.class),
                mock(CommandProgressContext.class));

        var inlinedModel = Path.of(java.net.URI.create((String) result));
        assertThat(Files.readString(inlinedModel)).contains("OnlyInBuffer");
    }
}
