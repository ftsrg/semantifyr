/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.lang.ide.server.ServerSettings;
import hu.bme.mit.semantifyr.lang.ide.server.commands.AbstractCommandHandler;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandProgressContext;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationStatus;
import hu.bme.mit.semantifyr.lang.ide.server.wire.WitnessValidationResult;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts;
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierFactory;
import hu.bme.mit.semantifyr.verifier.VerificationOutcome;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidateWitnessClassCommandHandler
        extends AbstractCommandHandler<VerificationCaseRequest, VerifyInlinedOxstsCommandParams> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidateWitnessClassCommandHandler.class);

    @Inject
    protected ServerSettings serverSettings;

    @Inject
    protected SemantifyrVerifierFactory semantifyrVerifierFactory;

    @Override
    public String getId() {
        return "oxsts.case.validateWitness";
    }

    @Override
    public String getTitle() {
        return "Validate witness";
    }

    @Override
    public List<Object> serializeArguments(VerifyInlinedOxstsCommandParams arguments) {
        return List.of(
                VerificationCaseRequest.fromLocation(getLocation(arguments.inlinedOxsts()), arguments.portfolioId()));
    }

    @Override
    protected Class<VerificationCaseRequest> getRequestType() {
        return VerificationCaseRequest.class;
    }

    @Override
    protected VerifyInlinedOxstsCommandParams resolveArgument(
            VerificationCaseRequest request, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        var element = getElement(access, request.toLocation());
        return new VerifyInlinedOxstsCommandParams((InlinedOxsts) element, request.portfolio());
    }

    @Override
    protected Object execute(
            VerifyInlinedOxstsCommandParams arguments,
            ILanguageServerAccess access,
            CommandProgressContext progressContext) {
        progressContext.begin("Validating witness", "Initializing");

        var inlinedOxsts = arguments.inlinedOxsts();
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
                VerificationOutcome outcome =
                        runBlocking(continuation -> verifier.verify(inlinedOxsts, progressContext, continuation));
                LOGGER.info(
                        "LSP validateWitness {}",
                        outcome.getReport().getResult().getVerdict());
                return WitnessValidationResult.fromOutcome(outcome, portfolio.getId());
            } catch (CancellationException e) {
                LOGGER.info("Witness validation cancelled");
                return WitnessValidationResult.cancelled(VerificationStatus.CANCELLED_MESSAGE, portfolio.getId());
            } catch (Exception e) {
                LOGGER.warn("Witness validation threw {}", e.getClass().getSimpleName(), e);
                return WitnessValidationResult.errored(e.toString(), portfolio.getId());
            }
        });
    }
}
