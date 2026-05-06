/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.ide.server.commands;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.frontends.gamma.GammaFrontend;
import hu.bme.mit.semantifyr.frontends.gamma.discovery.GammaVerificationCaseDiscoverer;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VerificationCaseDeclaration;
import hu.bme.mit.semantifyr.lang.ide.server.ServerSettings;
import hu.bme.mit.semantifyr.lang.ide.server.commands.AbstractCommandHandler;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandProgressContext;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult;
import java.util.List;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyGammaCaseCommandHandler
        extends AbstractCommandHandler<VerificationCaseRequest, VerifyGammaCommandParams> {

    private static final Logger LOGGER = LoggerFactory.getLogger(VerifyGammaCaseCommandHandler.class);

    @Inject
    protected GammaVerificationCaseDiscoverer gammaDiscoverer;

    @Inject
    protected ServerSettings serverSettings;

    @Override
    public String getId() {
        return "gamma.case.verify";
    }

    @Override
    public String getTitle() {
        return "Verify";
    }

    @Override
    public List<Object> serializeArguments(VerifyGammaCommandParams arguments) {
        return List.of(VerificationCaseRequest.fromLocation(
                getLocation(arguments.caseDeclaration()), arguments.portfolioId()));
    }

    @Override
    protected Class<VerificationCaseRequest> getRequestType() {
        return VerificationCaseRequest.class;
    }

    @Override
    protected VerifyGammaCommandParams resolveArgument(
            VerificationCaseRequest request, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        var element = getElement(access, request.toLocation());
        return new VerifyGammaCommandParams((VerificationCaseDeclaration) element, request.portfolio());
    }

    @Override
    protected Object execute(
            VerifyGammaCommandParams arguments, ILanguageServerAccess access, CommandProgressContext progressContext) {
        var caseDeclaration = arguments.caseDeclaration();
        var gammaCase = gammaDiscoverer.mapDeclaration(caseDeclaration);
        progressContext.begin("Verifying Gamma case " + gammaCase.getQualifiedName(), "Compiling Gamma to OXSTS");

        LOGGER.info("LSP verify request for Gamma case '{}'", gammaCase.getQualifiedName());

        var portfolio = serverSettings.resolvePortfolio(arguments.portfolioId());
        var outputDirectory = serverSettings.resolveArtifactOutputDirectory();

        semantifyrRequestManager.releaseReadLock();
        try {
            var frontend = GammaFrontend.builder()
                    .portfolio(portfolio)
                    .environment(serverSettings.resolveExecutionEnvironment())
                    .timeout(serverSettings.resolveTimeout())
                    .artifacts(serverSettings.resolveArtifactConfig())
                    .outputDirectory(outputDirectory)
                    .optimization(serverSettings.resolveOptimizationConfig())
                    .build();

            progressContext.checkIsCancelled();
            progressContext.reportProgress("Running backend verification");

            var gammaResult = frontend.verifyBlocking(gammaCase, progressContext);
            LOGGER.info(
                    "LSP verify '{}' -> {}",
                    gammaCase.getQualifiedName(),
                    gammaResult.getVerification().getVerdict());

            return VerificationCaseResult.fromDtoWithoutTrace(gammaResult.getVerification(), portfolio.getId());
        } catch (Exception e) {
            LOGGER.error(
                    "LSP verify '{}' threw {}",
                    gammaCase.getQualifiedName(),
                    e.getClass().getSimpleName(),
                    e);
            return null;
        } finally {
            semantifyrRequestManager.acquireReadLock();
        }
    }
}
