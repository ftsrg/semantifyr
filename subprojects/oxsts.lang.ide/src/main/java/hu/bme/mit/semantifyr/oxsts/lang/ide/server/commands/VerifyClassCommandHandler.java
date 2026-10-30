/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
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
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifier;
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer;
import java.util.List;
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
    protected VerificationCaseDiscoverer verificationCaseDiscoverer;

    @Inject
    protected ServerSettings serverSettings;

    @Inject
    protected Injector injector;

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
        LOGGER.info("LSP verify request for case '{}'", targetQualifiedName);
        var context =
                semantifyrLoader.fromResourceSet(classDeclaration.eResource().getResourceSet());

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

                var match = verificationCaseDiscoverer.findByQualifiedName(context, targetQualifiedName);

                var dto = verifier.verifyBlocking(match, progressContext);
                LOGGER.info("LSP verify '{}' -> {}", targetQualifiedName, dto.getVerdict());
                return VerificationCaseResult.fromDto(dto, portfolio.getId());
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
