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
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.verifier.CaseDirectoryNameKt;
import hu.bme.mit.semantifyr.verifier.VerificationCase;
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InlineClassCommandHandlerTest {

    private static final String CASE_NAME = "example::lights::GreenIsReachable";

    private static final Path OUTPUT_DIRECTORY = Path.of("artifacts");

    private static final Path FLATTENED_MODEL = Path.of("artifacts", "case", "pipeline", "flattened.semantifyr");

    private static final OptimizationConfig OPTIMIZATION = OptimizationConfig.Companion.getNONE();

    @Mock
    private OxstsQualifiedNameProvider oxstsQualifiedNameProvider;

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
    private InlineClassCommandHandler handler = new InlineClassCommandHandler() {
        @Override
        protected <T> T performBackgroundWork(Supplier<T> work) {
            return work.get();
        }
    };

    private ClassDeclaration classDeclaration;

    @BeforeEach
    void setUp() {
        classDeclaration = mock(ClassDeclaration.class);
        var resource = mock(Resource.class);
        var resourceSet = mock(ResourceSet.class);
        var openTexts = Map.of(URI.createURI("file:///workspace/lights.semantifyr"), "package example::lights");
        var clone = mock(SemantifyrModelContext.class);
        var clonedClassDeclaration = mock(ClassDeclaration.class);
        var verificationCase = new VerificationCase(CASE_NAME, clonedClassDeclaration, Set.of());
        var compiler = mock(SemantifyrCompiler.class);
        var compilation = mock(FlattenedCompilationContext.class);
        when(oxstsQualifiedNameProvider.getFullyQualifiedNameString(classDeclaration))
                .thenReturn(CASE_NAME);
        when(classDeclaration.eResource()).thenReturn(resource);
        when(resource.getResourceSet()).thenReturn(resourceSet);
        when(openDocumentTexts.of(resourceSet)).thenReturn(openTexts);
        when(semantifyrLoader.fromResourceSet(resourceSet, openTexts)).thenReturn(clone);
        when(serverSettings.resolveOptimizationConfig()).thenReturn(OPTIMIZATION);
        when(serverSettings.resolveArtifactOutputDirectory(access)).thenReturn(OUTPUT_DIRECTORY);
        when(semantifyrCompilerFactory.create(any(), any())).thenReturn(compiler);
        when(verificationCaseDiscoverer.findByQualifiedName(clone, CASE_NAME)).thenReturn(verificationCase);
        when(compiler.compile(
                        clonedClassDeclaration, CaseDirectoryNameKt.caseDirectory(OUTPUT_DIRECTORY, verificationCase)))
                .thenReturn(compilation);
        when(compilation.getFlattenedModelPath()).thenReturn(FLATTENED_MODEL);
    }

    @Test
    void inliningCompilesTheCaseOfTheOpenDocumentsWithTheServerSettingsAndReturnsItsFlattenedModel() {
        when(serverSettings.resolveArtifactConfig()).thenReturn(ArtifactConfig.NONE);

        var result = handler.execute(new InlineClassCommandParams(classDeclaration, false), access, progressContext);

        assertThat(result).isEqualTo(FLATTENED_MODEL.toUri().toString());
        verify(semantifyrCompilerFactory)
                .create(ArtifactConfig.NONE.withEnabled(ArtifactKind.FlattenedModel), OPTIMIZATION);
    }

    @Test
    void inliningWithStepsCompilesWithTheDebugArtifacts() {
        handler.execute(new InlineClassCommandParams(classDeclaration, true), access, progressContext);

        verify(semantifyrCompilerFactory).create(ArtifactConfig.DEBUG, OPTIMIZATION);
    }
}
