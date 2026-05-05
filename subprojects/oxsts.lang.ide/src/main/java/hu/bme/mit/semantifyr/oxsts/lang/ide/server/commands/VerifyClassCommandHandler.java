/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Injector;
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader;
import hu.bme.mit.semantifyr.lang.ide.server.ServerSettings;
import hu.bme.mit.semantifyr.lang.ide.server.commands.AbstractCommandHandler;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandProgressContext;
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifier;
import hu.bme.mit.semantifyr.verifier.VerificationResultDto;
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer;
import java.util.List;
import org.eclipse.lsp4j.Location;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyClassCommandHandler extends AbstractCommandHandler<VerifyClassCommandParams> {

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
        return List.of(getLocation(arguments.classDeclaration()), arguments.portfolioId());
    }

    @Override
    protected VerifyClassCommandParams parseArguments(
            List<Object> arguments, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        var gson = new GsonBuilder().create();
        var locationJson = (JsonObject) arguments.get(0);
        var location = gson.fromJson(locationJson, Location.class);
        var element = getElement(access, location);
        var portfolioId =
                locationJson.has("portfolio") && locationJson.get("portfolio").isJsonPrimitive()
                        ? locationJson.get("portfolio").getAsString()
                        : null;
        return new VerifyClassCommandParams((ClassDeclaration) element, portfolioId);
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
        var outputDirectory = serverSettings.resolveArtifactOutputDirectory();

        semantifyrRequestManager.releaseReadLock();
        var verifier = SemantifyrVerifier.builder()
                .injector(injector)
                .portfolio(portfolio)
                .environment(serverSettings.resolveExecutionEnvironment())
                .timeout(serverSettings.resolveTimeout())
                .artifacts(serverSettings.resolveArtifactConfig())
                .outputDirectory(outputDirectory)
                .optimization(serverSettings.resolveOptimizationConfig())
                .build();

        try {
            progressContext.checkIsCancelled();

            var match = verificationCaseDiscoverer.findByQualifiedName(context, targetQualifiedName);

            var result = verifier.verifyBlocking(match, progressContext);
            LOGGER.info("LSP verify '{}' -> {}", targetQualifiedName, result.getVerdict());
            return VerificationResultDto.Companion.fromResult(result);
        } catch (Exception e) {
            LOGGER.error(
                    "LSP verify '{}' threw {}",
                    targetQualifiedName,
                    e.getClass().getSimpleName(),
                    e);
            return null;
        } finally {
            semantifyrRequestManager.acquireReadLock();
        }
    }
}
