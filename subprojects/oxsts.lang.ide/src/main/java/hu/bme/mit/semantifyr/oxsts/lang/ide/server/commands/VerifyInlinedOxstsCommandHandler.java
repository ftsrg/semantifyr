/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts;
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifier;
import hu.bme.mit.semantifyr.verifier.VerificationResultDto;
import hu.bme.mit.semantifyr.verifier.witness.WitnessValidator;
import java.util.List;
import org.eclipse.lsp4j.Location;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyInlinedOxstsCommandHandler extends AbstractCommandHandler<VerifyInlinedOxstsCommandParams> {

    private static final Logger LOGGER = LoggerFactory.getLogger(VerifyInlinedOxstsCommandHandler.class);

    @Inject
    protected ServerSettings serverSettings;

    @Inject
    protected Injector injector;

    @Inject
    protected SemantifyrLoader semantifyrLoader;

    @Inject
    protected WitnessValidator witnessValidator;

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
        return List.of(getLocation(arguments.inlinedOxsts()));
    }

    @Override
    protected VerifyInlinedOxstsCommandParams parseArguments(
            List<Object> arguments, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        var gson = new GsonBuilder().create();
        var locationJson = (JsonObject) arguments.get(0);
        var location = gson.fromJson(locationJson, Location.class);
        var element = getElement(access, location);
        var portfolioId =
                locationJson.has("portfolio") && locationJson.get("portfolio").isJsonPrimitive()
                        ? locationJson.get("portfolio").getAsString()
                        : null;
        return new VerifyInlinedOxstsCommandParams((InlinedOxsts) element, portfolioId);
    }

    @Override
    protected Object execute(
            VerifyInlinedOxstsCommandParams arguments,
            ILanguageServerAccess access,
            CommandProgressContext progressContext) {
        progressContext.begin("Verifying inlined oxsts", "Initializing");

        var inlinedOxsts = arguments.inlinedOxsts();

        var context = semantifyrLoader.fromResourceSet(inlinedOxsts.eResource().getResourceSet());

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
            var result = verifier.verifyBlocking(inlinedOxsts, progressContext);
            LOGGER.info("LSP verify {}", result.getVerdict());
            return VerificationResultDto.Companion.fromResult(result);
        } catch (Exception e) {
            LOGGER.warn("Verification threw {}", e.getClass().getSimpleName(), e);
            return null;
        } finally {
            semantifyrRequestManager.acquireReadLock();
        }
    }
}
