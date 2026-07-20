/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import com.google.inject.Inject;
import com.google.inject.Injector;
import hu.bme.mit.semantifyr.lang.ide.server.ServerSettings;
import hu.bme.mit.semantifyr.lang.ide.server.commands.AbstractCommandHandler;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandProgressContext;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest;
import hu.bme.mit.semantifyr.lang.ide.server.wire.WitnessValidationResult;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts;
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifier;
import hu.bme.mit.semantifyr.verifier.VerificationResultDtoKt;
import hu.bme.mit.semantifyr.verifier.witness.WitnessValidator;
import java.util.List;
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
    protected Injector injector;

    @Inject
    protected WitnessValidator witnessValidator;

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
                var verifier = SemantifyrVerifier.builder()
                        .injector(injector)
                        .portfolio(portfolio)
                        .environment(serverSettings.resolveExecutionEnvironment())
                        .timeout(serverSettings.resolveTimeout())
                        .artifacts(serverSettings.resolveArtifactConfig())
                        .outputDirectory(outputDirectory)
                        .optimization(serverSettings.resolveOptimizationConfig())
                        .build();

                progressContext.checkIsCancelled();
                var validationResult = witnessValidator.validateBlocking(verifier, inlinedOxsts, progressContext);
                LOGGER.info(
                        "LSP validateWitness {}", validationResult.getClass().getSimpleName());
                var dto = VerificationResultDtoKt.toJavaDto(validationResult);
                return WitnessValidationResult.fromDto(dto, portfolio.getId());
            } catch (Exception e) {
                LOGGER.warn("Witness validation threw {}", e.getClass().getSimpleName(), e);
                return WitnessValidationResult.errored(e.toString(), portfolio.getId());
            }
        });
    }
}
