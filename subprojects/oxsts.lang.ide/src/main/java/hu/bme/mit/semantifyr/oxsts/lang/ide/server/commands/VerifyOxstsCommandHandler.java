/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Injector;
import hu.bme.mit.semantifyr.backend.ExecutionEnvironment;
import hu.bme.mit.semantifyr.backend.VerificationCase;
import hu.bme.mit.semantifyr.backend.VerificationResult;
import hu.bme.mit.semantifyr.backend.VerificationVerdict;
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.ServerSettings;
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.verification.SemantifyrVerifier;
import hu.bme.mit.semantifyr.verification.discovery.CaseFilter;
import hu.bme.mit.semantifyr.verification.portfolio.VerificationPortfolio;
import org.eclipse.lsp4j.Location;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class VerifyOxstsCommandHandler extends AbstractCommandHandler<ClassDeclaration> {

    private static final Logger LOG = LoggerFactory.getLogger(VerifyOxstsCommandHandler.class);

    @Inject
    protected OxstsQualifiedNameProvider oxstsQualifiedNameProvider;

    @Inject
    protected SemantifyrLoader semantifyrLoader;

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
    public List<Object> serializeArguments(ClassDeclaration arguments) {
        var location = getLocation(arguments);
        return List.of(location);
    }

    @Override
    protected ClassDeclaration parseArguments(List<Object> arguments, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        var gsonBuilder = new GsonBuilder();
        var gson = gsonBuilder.create();
        var locationJson = (JsonObject) arguments.get(0);
        var location = gson.fromJson(locationJson, Location.class);
        var element = getElement(access, location);

        return (ClassDeclaration) element;
    }

    @Override
    protected Object execute(ClassDeclaration arguments, ILanguageServerAccess access, CommandProgressContext progressContext) {
        progressContext.begin("Verifying class " + arguments.getName(), "Initializing");

        var targetQualifiedName = oxstsQualifiedNameProvider.getFullyQualifiedNameString(arguments);
        LOG.info("LSP verify request for case '{}'", targetQualifiedName);
        var context = semantifyrLoader.fromResourceSet(arguments.eResource().getResourceSet());

        semantifyrRequestManager.releaseReadLock();
        VerificationPortfolio portfolio = serverSettings.resolvePortfolio();
        ExecutionEnvironment environment = serverSettings.resolveExecutionEnvironment();
        SemantifyrVerifier.Builder builder = SemantifyrVerifier.builder()
                .injector(injector)
                .context(context)
                .portfolio(portfolio)
                .environment(environment)
                .timeout(serverSettings.resolveTimeout())
                .artifacts(serverSettings.resolveArtifactConfig())
                .optimization(serverSettings.resolveOptimizationConfig());
        Integer maxConcurrency = serverSettings.resolveMaxConcurrencyOrNull();
        if (maxConcurrency != null) {
            builder.maxConcurrency(maxConcurrency);
        }
        try (SemantifyrVerifier verifier = builder.build()) {
            progressContext.checkIsCancelled();

            VerificationCase match = verifier.verificationCases(CaseFilter.All.INSTANCE).stream()
                    .filter(c -> targetQualifiedName.equals(c.getQualifiedName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No verification case found for qualified name '" + targetQualifiedName + "'"));

            VerificationResult result = verifier.verifyBlocking(match, progressContext);
            LOG.info("LSP verify '{}' -> {}", targetQualifiedName, result.getVerdict());
            return toDto(result);
        } catch (Exception e) {
            LOG.error("LSP verify '{}' threw {}", targetQualifiedName, e.getClass().getSimpleName(), e);
            return new VerificationCaseRunResultDto("error", e.getMessage());
        } finally {
            semantifyrRequestManager.acquireReadLock();
        }
    }

    private VerificationCaseRunResultDto toDto(VerificationResult result) {
        String status;
        if (result.getVerdict() == VerificationVerdict.Passed) {
            status = "passed";
        } else if (result.getVerdict() == VerificationVerdict.Failed) {
            status = "failed";
        } else {
            status = "error";
        }
        return new VerificationCaseRunResultDto(status, result.getMessage());
    }

}
