/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment;
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig;
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig;
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader;
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrModelContext;
import hu.bme.mit.semantifyr.lang.ide.server.OpenDocumentTexts;
import hu.bme.mit.semantifyr.lang.ide.server.ServerSettings;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandProgressContext;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationStatus;
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifier;
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierFactory;
import hu.bme.mit.semantifyr.verifier.VerificationCase;
import hu.bme.mit.semantifyr.verifier.VerificationOutcome;
import hu.bme.mit.semantifyr.verifier.VerificationReport;
import hu.bme.mit.semantifyr.verifier.VerificationResult;
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer;
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
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
class VerifyClassCommandHandlerTest {

    private static final String CASE_NAME = "example::lights::GreenIsReachable";

    private static final String PORTFOLIO_ID = "theta";

    private static final Path OUTPUT_DIRECTORY = Path.of("artifacts");

    private static final Duration TIMEOUT = Duration.ofSeconds(7);

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
    private SemantifyrVerifierFactory semantifyrVerifierFactory;

    @Mock
    private ILanguageServerAccess access;

    @Mock
    private CommandProgressContext progressContext;

    @InjectMocks
    private VerifyClassCommandHandler handler = new VerifyClassCommandHandler() {
        @Override
        protected <T> T performBackgroundWork(Supplier<T> work) {
            return work.get();
        }
    };

    private ClassDeclaration classDeclaration;

    private SemantifyrModelContext clone;

    private VerificationPortfolio portfolio;

    private ExecutionEnvironment environment;

    private SemantifyrVerifier verifier;

    @BeforeEach
    void setUp() {
        classDeclaration = mock(ClassDeclaration.class);
        var resource = mock(Resource.class);
        var resourceSet = mock(ResourceSet.class);
        var openTexts = Map.of(URI.createURI("file:///workspace/lights.semantifyr"), "package example::lights");
        clone = mock(SemantifyrModelContext.class);
        portfolio = mock(VerificationPortfolio.class);
        environment = mock(ExecutionEnvironment.class);
        verifier = mock(SemantifyrVerifier.class);
        when(oxstsQualifiedNameProvider.getFullyQualifiedNameString(classDeclaration))
                .thenReturn(CASE_NAME);
        when(classDeclaration.eResource()).thenReturn(resource);
        when(resource.getResourceSet()).thenReturn(resourceSet);
        when(openDocumentTexts.of(resourceSet)).thenReturn(openTexts);
        when(semantifyrLoader.fromResourceSet(resourceSet, openTexts)).thenReturn(clone);
        when(serverSettings.resolvePortfolio(PORTFOLIO_ID)).thenReturn(portfolio);
        when(serverSettings.resolveArtifactOutputDirectory(access)).thenReturn(OUTPUT_DIRECTORY);
        when(serverSettings.resolveExecutionEnvironment()).thenReturn(environment);
        when(serverSettings.resolveTimeout()).thenReturn(TIMEOUT);
        when(serverSettings.resolveArtifactConfig()).thenReturn(ArtifactConfig.NONE);
        when(serverSettings.resolveOptimizationConfig()).thenReturn(OPTIMIZATION);
        when(semantifyrVerifierFactory.create(any(), any(), any(), any(), any(), any()))
                .thenReturn(verifier);
    }

    private Object execute() {
        return handler.execute(new VerifyClassCommandParams(classDeclaration, PORTFOLIO_ID), access, progressContext);
    }

    @Test
    void verificationVerifiesTheCaseOfTheOpenDocumentsWithTheServerSettingsAndReturnsItsOutcome() {
        var verificationCase = new VerificationCase(CASE_NAME, mock(ClassDeclaration.class), Set.of());
        var report = mock(VerificationReport.class);
        var outcome = new VerificationOutcome(report, Path.of("report.json"), null);
        when(verificationCaseDiscoverer.findByQualifiedName(clone, CASE_NAME)).thenReturn(verificationCase);
        // Mockito leaves the continuation of a suspending call out of matching
        when(verifier.verify(verificationCase, progressContext, null)).thenReturn(outcome);
        when(report.getResult()).thenReturn(mock(VerificationResult.class));

        var result = execute();

        assertThat(result).isEqualTo(VerificationCaseResult.fromOutcome(outcome, null));
        verify(semantifyrVerifierFactory)
                .create(portfolio, environment, TIMEOUT, ArtifactConfig.NONE, OUTPUT_DIRECTORY, OPTIMIZATION);
    }

    @Test
    void aVerificationCancelledThroughItsProgressIsAnsweredCancelledWithoutVerifying() {
        doThrow(new CancellationException()).when(progressContext).checkIsCancelled();
        when(portfolio.getId()).thenReturn(PORTFOLIO_ID);

        var result = execute();

        assertThat(result)
                .isEqualTo(VerificationCaseResult.cancelled(VerificationStatus.CANCELLED_MESSAGE, PORTFOLIO_ID));
        verifyNoInteractions(verifier);
    }
}
