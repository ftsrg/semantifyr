/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader;
import hu.bme.mit.semantifyr.lang.ide.server.OpenDocumentTexts;
import hu.bme.mit.semantifyr.lang.ide.server.ServerSettings;
import hu.bme.mit.semantifyr.lang.ide.server.WitnessOwners;
import hu.bme.mit.semantifyr.lang.ide.server.commands.AbstractCommandHandler;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandProgressContext;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationStatus;
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierFactory;
import hu.bme.mit.semantifyr.verifier.VerificationOutcome;
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyClassCommandHandler
        extends AbstractCommandHandler<VerificationCaseRequest, VerifyClassCommandParams> {

    private static final Logger LOGGER = LoggerFactory.getLogger(VerifyClassCommandHandler.class);

    @Inject
    protected OxstsQualifiedNameProvider oxstsQualifiedNameProvider;

    @Inject
    protected SemantifyrLoader semantifyrLoader;

    @Inject
    protected OpenDocumentTexts openDocumentTexts;

    @Inject
    protected VerificationCaseDiscoverer verificationCaseDiscoverer;

    @Inject
    protected ServerSettings serverSettings;

    @Inject
    protected SemantifyrVerifierFactory semantifyrVerifierFactory;

    @Inject
    protected WitnessOwners witnessOwners;

    @Override
    public String getId() {
        return "oxsts.case.verify";
    }

    @Override
    public String getTitle() {
        return "Verify";
    }

    @Override
    public List<Object> serializeArguments(VerifyClassCommandParams arguments) {
        return List.of(VerificationCaseRequest.fromLocation(
                getLocation(arguments.classDeclaration()), arguments.portfolioId()));
    }

    @Override
    protected Class<VerificationCaseRequest> getRequestType() {
        return VerificationCaseRequest.class;
    }

    @Override
    protected VerifyClassCommandParams resolveArgument(
            VerificationCaseRequest request, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        var element = getElement(access, request.toLocation());
        return new VerifyClassCommandParams((ClassDeclaration) element, request.portfolio());
    }

    @Override
    protected Object execute(
            VerifyClassCommandParams arguments, ILanguageServerAccess access, CommandProgressContext progressContext) {
        var classDeclaration = arguments.classDeclaration();
        progressContext.begin("Verifying class " + classDeclaration.getName(), "Initializing");

        var targetQualifiedName = oxstsQualifiedNameProvider.getFullyQualifiedNameString(classDeclaration);
        var caseResource = classDeclaration.eResource().getURI();
        LOGGER.info("LSP verify request for case '{}'", targetQualifiedName);
        var context = semantifyrLoader.fromResourceSet(
                classDeclaration.eResource().getResourceSet(),
                openDocumentTexts.of(classDeclaration.eResource().getResourceSet()));

        var portfolio = serverSettings.resolvePortfolio(arguments.portfolioId());
        var outputDirectory = serverSettings.resolveArtifactOutputDirectory(access);

        return performBackgroundWork(() -> {
            try {
                var verifier = semantifyrVerifierFactory.create(
                        portfolio,
                        serverSettings.resolveExecutionEnvironment(),
                        serverSettings.resolveTimeout(),
                        serverSettings.resolveArtifactConfig(),
                        outputDirectory,
                        serverSettings.resolveOptimizationConfig());

                progressContext.checkIsCancelled();

                var match = verificationCaseDiscoverer.findByQualifiedName(context, targetQualifiedName);

                VerificationOutcome outcome =
                        runBlocking(continuation -> verifier.verify(match, progressContext, continuation));
                LOGGER.info(
                        "LSP verify '{}' -> {}",
                        targetQualifiedName,
                        outcome.getReport().getResult().getVerdict());
                var result = VerificationCaseResult.fromOutcome(outcome, null);
                if (serverSettings.keepsTemporaryArtifacts() && result.witnessUri() != null) {
                    witnessOwners.record(URI.createURI(result.witnessUri()), caseResource);
                }
                return result;
            } catch (CancellationException e) {
                LOGGER.info("LSP verify '{}' cancelled", targetQualifiedName);
                return VerificationCaseResult.cancelled(VerificationStatus.CANCELLED_MESSAGE, portfolio.getId());
            } catch (Exception e) {
                LOGGER.error(
                        "LSP verify '{}' threw {}",
                        targetQualifiedName,
                        e.getClass().getSimpleName(),
                        e);
                return VerificationCaseResult.errored(e.toString(), portfolio.getId());
            }
        });
    }
}
