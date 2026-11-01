/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.ide.server.commands;

import com.google.inject.Inject;
import com.google.inject.Injector;
import hu.bme.mit.semantifyr.frontends.gamma.GammaFrontend;
import hu.bme.mit.semantifyr.frontends.gamma.discovery.GammaVerificationCaseDiscoverer;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VerificationCaseDeclaration;
import hu.bme.mit.semantifyr.lang.ide.server.OxstsInjector;
import hu.bme.mit.semantifyr.lang.ide.server.ServerSettings;
import hu.bme.mit.semantifyr.lang.ide.server.commands.AbstractCommandHandler;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandProgressContext;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest;
import hu.bme.mit.semantifyr.lang.ide.server.wire.WitnessValidationResult;
import java.util.List;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidateWitnessGammaCommandHandler
        extends AbstractCommandHandler<VerificationCaseRequest, ValidateWitnessCommandParams> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidateWitnessGammaCommandHandler.class);

    @Inject
    protected GammaVerificationCaseDiscoverer gammaDiscoverer;

    @Inject
    protected ServerSettings serverSettings;

    @Inject
    protected Injector gammaInjector;

    @Inject
    protected OxstsInjector oxstsInjector;

    @Override
    public String getId() {
        return "gamma.witness.validate";
    }

    @Override
    public String getTitle() {
        return "Validate witness";
    }

    @Override
    public List<Object> serializeArguments(ValidateWitnessCommandParams arguments) {
        return List.of(VerificationCaseRequest.fromLocation(
                getLocation(arguments.caseDeclaration()), arguments.portfolioId()));
    }

    @Override
    protected Class<VerificationCaseRequest> getRequestType() {
        return VerificationCaseRequest.class;
    }

    @Override
    protected ValidateWitnessCommandParams resolveArgument(
            VerificationCaseRequest request, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        var element = getElement(access, request.toLocation());
        return new ValidateWitnessCommandParams((VerificationCaseDeclaration) element, request.portfolio());
    }

    @Override
    protected Object execute(
            ValidateWitnessCommandParams arguments,
            ILanguageServerAccess access,
            CommandProgressContext progressContext) {
        var gammaCase = gammaDiscoverer.mapDeclaration(arguments.caseDeclaration());
        progressContext.begin(
                "Validating the witness of Gamma case " + gammaCase.getQualifiedName(), "Loading the witness");

        LOGGER.info("LSP witness validation request for Gamma case '{}'", gammaCase.getQualifiedName());

        var portfolio = serverSettings.resolvePortfolio(arguments.portfolioId());
        var outputDirectory = serverSettings.resolveArtifactOutputDirectory(access);

        return performBackgroundWork(() -> {
            try {
                var frontend = GammaFrontend.builder()
                        .gammaInjector(gammaInjector)
                        .oxstsInjector(oxstsInjector.get())
                        .portfolio(portfolio)
                        .environment(serverSettings.resolveExecutionEnvironment())
                        .timeout(serverSettings.resolveTimeout())
                        .artifacts(serverSettings.resolveArtifactConfig())
                        .outputDirectory(outputDirectory)
                        .optimization(serverSettings.resolveOptimizationConfig())
                        .build();

                var witnessPath = frontend.findWitness(gammaCase);
                if (witnessPath == null) {
                    LOGGER.info("LSP validate witness '{}' found no witness", gammaCase.getQualifiedName());
                    return WitnessValidationResult.errored(
                            "Case " + gammaCase.getQualifiedName() + " has no witness yet, verify it first",
                            portfolio.getId());
                }

                progressContext.checkIsCancelled();

                var validation = frontend.validateWitnessBlocking(gammaCase, witnessPath, progressContext);
                LOGGER.info(
                        "LSP validate witness '{}' -> {}",
                        gammaCase.getQualifiedName(),
                        validation.getVerification().getVerdict());

                return WitnessValidationResult.fromDto(validation, portfolio.getId());
            } catch (Exception e) {
                LOGGER.error(
                        "LSP validate witness '{}' threw {}",
                        gammaCase.getQualifiedName(),
                        e.getClass().getSimpleName(),
                        e);
                return WitnessValidationResult.errored(e.toString(), portfolio.getId());
            }
        });
    }
}
