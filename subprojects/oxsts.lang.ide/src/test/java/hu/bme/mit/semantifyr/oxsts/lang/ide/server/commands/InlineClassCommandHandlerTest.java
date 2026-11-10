/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.inject.Injector;
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandProgressContext;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsIdeSetup;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InlineClassCommandHandlerTest {

    private static final Path SMOKE_MODEL = Path.of("../../oxsts-test-models/smoke/smoke.oxsts");

    private static final String SMOKE_CASE = "semantifyr::verification::smoke::CounterReachesThree";

    private InlineClassCommandHandler handler;

    private ResourceSet resourceSet;

    private ClassDeclaration classDeclaration;

    @BeforeEach
    void setUp() {
        Injector injector = new OxstsIdeSetup().createInjectorAndDoEMFRegistration();
        var context = injector.getInstance(SemantifyrLoader.class).loadStandaloneModel(SMOKE_MODEL);
        resourceSet = context.getResourceSet();
        classDeclaration = injector.getInstance(VerificationCaseDiscoverer.class)
                .findByQualifiedName(context, SMOKE_CASE)
                .getClassDeclaration();
        handler = new InlineClassCommandHandler() {
            @Override
            protected <T> T performBackgroundWork(Supplier<T> work) {
                return work.get();
            }
        };
        injector.injectMembers(handler);
    }

    private Path inline(boolean serializeSteps) {
        var result = handler.execute(
                new InlineClassCommandParams(classDeclaration, serializeSteps),
                mock(ILanguageServerAccess.class),
                mock(CommandProgressContext.class));
        return Path.of(URI.create((String) result));
    }

    @Test
    void inliningWritesTheInlinedModelAndReturnsItsUri() throws Exception {
        var resourceCount = resourceSet.getResources().size();

        var inlinedModel = inline(false);

        assertThat(inlinedModel).hasFileName("inlined.oxsts").isRegularFile();
        assertThat(Files.readString(inlinedModel)).contains("CounterReachesThree");
        assertThat(inlinedModel.resolveSibling("pipeline").resolve("steps")).doesNotExist();
        assertThat(resourceSet.getResources()).hasSize(resourceCount);
    }

    @Test
    void inliningWithStepsWritesTheCompilationSteps() {
        var inlinedModel = inline(true);

        assertThat(inlinedModel).isRegularFile();
        assertThat(inlinedModel.resolveSibling("pipeline").resolve("steps")).isNotEmptyDirectory();
    }

    @Test
    void inliningTwiceWritesTheModelAgain() {
        var first = inline(false);

        var second = inline(false);

        assertThat(second).isRegularFile();
        assertThat(first.getFileName()).isEqualTo(second.getFileName());
    }
}
