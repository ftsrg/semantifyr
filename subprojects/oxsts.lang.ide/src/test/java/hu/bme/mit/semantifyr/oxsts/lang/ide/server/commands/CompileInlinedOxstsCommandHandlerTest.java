/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler;
import hu.bme.mit.semantifyr.compiler.SemantifyrCompilerFactory;
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig;
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactKind;
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext;
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig;
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader;
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrModelContext;
import hu.bme.mit.semantifyr.lang.ide.server.OpenDocumentTexts;
import hu.bme.mit.semantifyr.lang.ide.server.ServerSettings;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandProgressContext;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsFactory;
import hu.bme.mit.semantifyr.verifier.CaseDirectoryNameKt;
import hu.bme.mit.semantifyr.verifier.VerificationCase;
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompileInlinedOxstsCommandHandlerTest {

    private static final Path OUTPUT_DIRECTORY = Path.of("artifacts");

    private static final Path FLATTENED_MODEL =
            Path.of("artifacts", "case", "compiled", "pipeline", "flattened.semantifyr");

    private static final OptimizationConfig OPTIMIZATION = OptimizationConfig.Companion.getNONE();

    @Mock
    private SemantifyrLoader semantifyrLoader;

    @Mock
    private OpenDocumentTexts openDocumentTexts;

    @Mock
    private VerificationCaseDiscoverer verificationCaseDiscoverer;

    @Mock
    private ServerSettings serverSettings;

    @Mock
    private SemantifyrCompilerFactory semantifyrCompilerFactory;

    @Mock
    private ILanguageServerAccess access;

    @Mock
    private CommandProgressContext progressContext;

    @InjectMocks
    private CompileInlinedOxstsCommandHandler handler = new CompileInlinedOxstsCommandHandler() {
        @Override
        protected <T> T performBackgroundWork(Supplier<T> work) {
            return work.get();
        }
    };

    @Test
    void compilingCompilesTheInlinedModelOfTheOpenDocumentsIntoTheCompiledDirectoryOfItsCase() {
        var inlinedOxsts = OxstsFactory.eINSTANCE.createInlinedOxsts();
        var resource = new ResourceImpl(URI.createURI("file:///workspace/flattened.semantifyr"));
        resource.getContents().add(inlinedOxsts);
        var resourceSet = new ResourceSetImpl();
        resourceSet.getResources().add(resource);
        var openTexts = Map.of(resource.getURI(), "inlined oxsts of example::lights::GreenIsReachable");
        var clone = mock(SemantifyrModelContext.class);
        var cloneResourceSet = mock(ResourceSet.class);
        var clonedInlinedOxsts = mock(InlinedOxsts.class);
        var classDeclaration = mock(ClassDeclaration.class);
        var verificationCase = new VerificationCase("example::lights::GreenIsReachable", classDeclaration, Set.of());
        var compiler = mock(SemantifyrCompiler.class);
        var compilation = mock(FlattenedCompilationContext.class);
        when(openDocumentTexts.of(resourceSet)).thenReturn(openTexts);
        when(semantifyrLoader.fromResourceSet(resourceSet, openTexts)).thenReturn(clone);
        when(clone.getResourceSet()).thenReturn(cloneResourceSet);
        when(cloneResourceSet.getEObject(EcoreUtil.getURI(inlinedOxsts), true)).thenReturn(clonedInlinedOxsts);
        when(serverSettings.resolveArtifactConfig()).thenReturn(ArtifactConfig.NONE);
        when(serverSettings.resolveOptimizationConfig()).thenReturn(OPTIMIZATION);
        when(serverSettings.resolveArtifactOutputDirectory(access)).thenReturn(OUTPUT_DIRECTORY);
        when(semantifyrCompilerFactory.create(any(), any())).thenReturn(compiler);
        when(clonedInlinedOxsts.getClassDeclaration()).thenReturn(classDeclaration);
        when(verificationCaseDiscoverer.caseOf(classDeclaration)).thenReturn(verificationCase);
        when(compiler.compile(
                        clonedInlinedOxsts,
                        CaseDirectoryNameKt.caseDirectory(OUTPUT_DIRECTORY, verificationCase)
                                .resolve("compiled")))
                .thenReturn(compilation);
        when(compilation.getFlattenedModelPath()).thenReturn(FLATTENED_MODEL);

        var result = handler.execute(inlinedOxsts, access, progressContext);

        assertThat(result).isEqualTo(FLATTENED_MODEL.toUri().toString());
        verify(semantifyrCompilerFactory)
                .create(ArtifactConfig.NONE.withEnabled(ArtifactKind.FlattenedModel), OPTIMIZATION);
    }
}
