/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server;

import static hu.bme.mit.semantifyr.backends.theta.ThetaXstsExecutorKt.ThetaExecutorKey;
import static java.util.Objects.requireNonNullElse;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment;
import hu.bme.mit.semantifyr.backends.theta.ThetaXstsExecutor;
import hu.bme.mit.semantifyr.backends.theta.ThetaXstsExecutorKt;
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig;
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig;
import hu.bme.mit.semantifyr.lang.ide.server.wire.ArtifactsLocation;
import hu.bme.mit.semantifyr.lang.ide.server.wire.ArtifactsPreset;
import hu.bme.mit.semantifyr.lang.ide.server.wire.OptimizationLevel;
import hu.bme.mit.semantifyr.lang.ide.server.wire.ServerSettingsPayload;
import hu.bme.mit.semantifyr.lang.ide.server.wire.ThetaExecutor;
import hu.bme.mit.semantifyr.portfolios.Portfolios;
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ServerSettings {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerSettings.class);
    private static final Gson GSON = new Gson();

    public static final String DEFAULT_PORTFOLIO_ID = "theta-full";
    private static final String WORKSPACE_ARTIFACTS_SUBDIRECTORY = ".artifacts";

    private static final ServerSettingsPayload DEFAULTS = new ServerSettingsPayload(
            DEFAULT_PORTFOLIO_ID,
            300L,
            0,
            new ServerSettingsPayload.Theta(ThetaExecutor.AUTO, ThetaXstsExecutorKt.THETA_DEFAULT_IMAGE),
            new ServerSettingsPayload.Artifacts(ArtifactsLocation.TEMPORARY, ArtifactsPreset.ALL),
            new ServerSettingsPayload.Optimization(OptimizationLevel.ALL));

    private final AtomicReference<ServerSettingsPayload> effective = new AtomicReference<>(DEFAULTS);

    public void apply(JsonElement payload) {
        if (payload == null || payload.isJsonNull()) {
            return;
        }
        if (!(payload instanceof JsonObject root)) {
            LOGGER.warn("Ignoring non-object LSP settings payload: {}", payload);
            return;
        }
        JsonObject section = root.has("semantifyr") && root.get("semantifyr").isJsonObject()
                ? root.getAsJsonObject("semantifyr")
                : root;
        apply(GSON.fromJson(section, ServerSettingsPayload.class));
    }

    public void apply(ServerSettingsPayload payload) {
        if (payload == null) {
            return;
        }
        ServerSettingsPayload next = withDefaults(payload);
        effective.set(next);
        LOGGER.info("LSP settings applied: {}", next);
    }

    private static ServerSettingsPayload withDefaults(ServerSettingsPayload payload) {
        ServerSettingsPayload.Theta theta = payload.theta();
        ServerSettingsPayload.Artifacts artifacts = payload.artifacts();
        ServerSettingsPayload.Optimization optimization = payload.optimization();
        return new ServerSettingsPayload(
                requireNonNullElse(payload.portfolio(), DEFAULTS.portfolio()),
                requireNonNullElse(payload.timeoutSeconds(), DEFAULTS.timeoutSeconds()),
                requireNonNullElse(payload.maxConcurrency(), DEFAULTS.maxConcurrency()),
                new ServerSettingsPayload.Theta(
                        requireNonNullElse(
                                theta != null ? theta.executor() : null,
                                DEFAULTS.theta().executor()),
                        requireNonNullElse(
                                theta != null ? theta.dockerImage() : null,
                                DEFAULTS.theta().dockerImage())),
                new ServerSettingsPayload.Artifacts(
                        requireNonNullElse(
                                artifacts != null ? artifacts.location() : null,
                                DEFAULTS.artifacts().location()),
                        requireNonNullElse(
                                artifacts != null ? artifacts.preset() : null,
                                DEFAULTS.artifacts().preset())),
                new ServerSettingsPayload.Optimization(requireNonNullElse(
                        optimization != null ? optimization.level() : null,
                        DEFAULTS.optimization().level())));
    }

    public VerificationPortfolio resolvePortfolio(String portfolioIdOverride) {
        if (portfolioIdOverride != null && !portfolioIdOverride.isBlank()) {
            VerificationPortfolio override = Portfolios.INSTANCE.byIdOrNull(portfolioIdOverride);
            if (override != null) {
                return override;
            }
            LOGGER.warn("Unknown per-call portfolio override '{}'; falling back to settings", portfolioIdOverride);
        }
        String id = effective.get().portfolio();
        VerificationPortfolio portfolio = Portfolios.INSTANCE.byIdOrNull(id);
        if (portfolio == null) {
            LOGGER.warn("Unknown portfolio '{}', falling back to '{}'", id, DEFAULT_PORTFOLIO_ID);
            return Portfolios.INSTANCE.byIdOrNull(DEFAULT_PORTFOLIO_ID);
        }
        return portfolio;
    }

    public Duration resolveTimeout() {
        return Duration.ofSeconds(effective.get().timeoutSeconds());
    }

    public Integer resolveMaxConcurrencyOrNull() {
        int value = effective.get().maxConcurrency();
        return value > 0 ? value : null;
    }

    public ExecutionEnvironment resolveExecutionEnvironment() {
        ServerSettingsPayload.Theta theta = effective.get().theta();
        String image = theta.dockerImage();
        return switch (theta.executor()) {
            case SHELL ->
                ExecutionEnvironment.builder()
                        .put(ThetaExecutorKey, ThetaXstsExecutor.Companion::shell)
                        .build();
            case DOCKER ->
                ExecutionEnvironment.builder()
                        .put(ThetaExecutorKey, () -> ThetaXstsExecutor.Companion.docker(image))
                        .build();
            case AUTO ->
                ExecutionEnvironment.builder()
                        .put(ThetaExecutorKey, () -> ThetaXstsExecutor.Companion.autoDetect(image))
                        .build();
        };
    }

    public ArtifactConfig resolveArtifactConfig() {
        return switch (effective.get().artifacts().preset()) {
            case NONE -> ArtifactConfig.NONE;
            case ALL -> ArtifactConfig.ALL;
            case DEBUG -> ArtifactConfig.DEBUG;
        };
    }

    public Path resolveArtifactOutputDirectory(ILanguageServerAccess access) {
        return switch (effective.get().artifacts().location()) {
            case WORKSPACE -> workspaceArtifactDirectory(access);
            case TEMPORARY -> createTempDir();
        };
    }

    private Path workspaceArtifactDirectory(ILanguageServerAccess access) {
        Path workspaceRoot = workspaceRootOrNull(access);
        if (workspaceRoot == null) {
            LOGGER.warn("artifacts.location=workspace but no workspace root is available, using a temporary directory");
            return createTempDir();
        }
        Path artifactDirectory = workspaceRoot.resolve(WORKSPACE_ARTIFACTS_SUBDIRECTORY);
        try {
            Files.createDirectories(artifactDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create workspace artifact directory " + artifactDirectory, e);
        }
        return artifactDirectory;
    }

    private static Path workspaceRootOrNull(ILanguageServerAccess access) {
        InitializeParams params = access.getInitializeParams();
        if (params == null) {
            return null;
        }
        String rootUri = params.getRootUri();
        if (rootUri == null || rootUri.isBlank()) {
            return null;
        }
        try {
            return Path.of(URI.create(rootUri));
        } catch (RuntimeException e) {
            LOGGER.warn("Workspace rootUri '{}' is not a usable file path: {}", rootUri, e.getMessage());
            return null;
        }
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("semantifyr-");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temporary directory for artifacts", e);
        }
    }

    public OptimizationConfig resolveOptimizationConfig() {
        return switch (effective.get().optimization().level()) {
            case NONE -> OptimizationConfig.Companion.getNONE();
            case ALL -> OptimizationConfig.Companion.getALL();
        };
    }
}
