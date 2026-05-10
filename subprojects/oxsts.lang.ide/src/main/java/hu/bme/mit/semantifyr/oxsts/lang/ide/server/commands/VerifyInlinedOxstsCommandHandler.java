/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import com.google.inject.Inject;
import com.google.inject.Injector;
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader;
import hu.bme.mit.semantifyr.lang.ide.server.ServerSettings;
import hu.bme.mit.semantifyr.lang.ide.server.commands.AbstractCommandHandler;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandProgressContext;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseRequest;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts;
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifier;
import java.util.List;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyInlinedOxstsCommandHandler
        extends AbstractCommandHandler<VerificationCaseRequest, VerifyInlinedOxstsCommandParams> {

    private static final Logger LOGGER = LoggerFactory.getLogger(VerifyInlinedOxstsCommandHandler.class);

    @Inject
    protected ServerSettings serverSettings;

    @Inject
    protected Injector injector;

    @Inject
    protected SemantifyrLoader semantifyrLoader;

    @Override
    public String getId() {
        return "oxsts.inlined.verify";
    }

    @Override
    public String getTitle() {
        return "Verify inlined oxsts";
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
        progressContext.begin("Verifying inlined oxsts", "Initializing");

        var inlinedOxsts = arguments.inlinedOxsts();
        var portfolio = serverSettings.resolvePortfolio(arguments.portfolioId());
        var outputDirectory = serverSettings.resolveArtifactOutputDirectory();

        return semantifyrRequestManager.performBackgroundWork(() -> {
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
                var dto = verifier.verifyBlocking(inlinedOxsts, progressContext);
                LOGGER.info("LSP verify {}", dto.getVerdict());
                return VerificationCaseResult.fromDto(dto, portfolio.getId());
            } catch (Exception e) {
                LOGGER.warn("Verification threw {}", e.getClass().getSimpleName(), e);
                return null;
            }
        });
    }
}
