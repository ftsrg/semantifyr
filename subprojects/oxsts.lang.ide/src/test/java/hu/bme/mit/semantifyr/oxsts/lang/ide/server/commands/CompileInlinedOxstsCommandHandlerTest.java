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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts;
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.serializer.ISerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompileInlinedOxstsCommandHandlerTest {

    private static final Path SMOKE_MODEL = Path.of("../../oxsts-test-models/smoke/smoke.oxsts");

    private static final String SMOKE_CASE = "semantifyr::verification::smoke::CounterReachesThree";

    private CompileInlinedOxstsCommandHandler handler;

    private ResourceSet resourceSet;

    private Path inlinedModel;

    private InlinedOxsts inlinedOxsts;

    private ISerializer serializer;

    @BeforeEach
    void setUp() {
        Injector injector = new OxstsIdeSetup().createInjectorAndDoEMFRegistration();
        var context = injector.getInstance(SemantifyrLoader.class).loadStandaloneModel(SMOKE_MODEL);
        resourceSet = context.getResourceSet();
        serializer = injector.getInstance(ISerializer.class);
        var classDeclaration = injector.getInstance(VerificationCaseDiscoverer.class)
                .findByQualifiedName(context, SMOKE_CASE)
                .getClassDeclaration();
        var inlineHandler = new InlineClassCommandHandler() {
            @Override
            protected <T> T performBackgroundWork(Supplier<T> work) {
                return work.get();
            }
        };
        injector.injectMembers(inlineHandler);
        var inlined = inlineHandler.execute(
                new InlineClassCommandParams(classDeclaration, false),
                mock(ILanguageServerAccess.class),
                mock(CommandProgressContext.class));
        inlinedModel = Path.of(URI.createURI((String) inlined).toFileString());
        var inlinedResource = resourceSet.getResource(URI.createFileURI(inlinedModel.toString()), true);
        inlinedOxsts = (InlinedOxsts) inlinedResource.getContents().getFirst();
        handler = new CompileInlinedOxstsCommandHandler() {
            @Override
            protected <T> T performBackgroundWork(Supplier<T> work) {
                return work.get();
            }
        };
        injector.injectMembers(handler);
    }

    @Test
    void compilingAnInlinedModelWritesTheCompiledModelAndReturnsItsUri() throws Exception {
        var resourceCount = resourceSet.getResources().size();
        var inlinedText = Files.readString(inlinedModel);
        var openModel = serializer.serialize(inlinedOxsts);

        var result =
                handler.execute(inlinedOxsts, mock(ILanguageServerAccess.class), mock(CommandProgressContext.class));

        var compiledModel = Path.of(URI.createURI((String) result).toFileString());
        assertThat(compiledModel).hasFileName("compiled.oxsts").isRegularFile();
        assertThat(Files.readString(compiledModel)).contains("CounterReachesThree");
        assertThat(Files.readString(inlinedModel)).isEqualTo(inlinedText);
        assertThat(serializer.serialize(inlinedOxsts)).isEqualTo(openModel);
        assertThat(resourceSet.getResources()).hasSize(resourceCount);
    }
}
