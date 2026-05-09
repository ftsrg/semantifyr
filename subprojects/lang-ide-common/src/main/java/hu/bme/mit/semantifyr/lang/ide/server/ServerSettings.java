/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server;

import static hu.bme.mit.semantifyr.backends.theta.ThetaXstsExecutorKt.ThetaExecutorKey;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment;
import hu.bme.mit.semantifyr.backends.theta.ThetaXstsExecutor;
import hu.bme.mit.semantifyr.backends.theta.ThetaXstsExecutorKt;
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig;
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig;
import hu.bme.mit.semantifyr.portfolios.Portfolios;
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ServerSettings {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerSettings.class);

    public static final String DEFAULT_PORTFOLIO_ID = "theta-full";
    public static final long DEFAULT_TIMEOUT_SECONDS = 300L;
    public static final String DEFAULT_THETA_EXECUTOR = "auto";
    public static final String DEFAULT_ARTIFACTS_LOCATION = "temporary";
    public static final String DEFAULT_ARTIFACTS_PRESET = "all";
    public static final String DEFAULT_OPTIMIZATION_LEVEL = "all";

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.defaults());

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
        Snapshot next = Snapshot.fromJson(section);
        snapshot.set(next);
        LOGGER.info("LSP settings applied: {}", next);
    }

    public VerificationPortfolio resolvePortfolio(String portfolioIdOverride) {
        if (portfolioIdOverride != null && !portfolioIdOverride.isBlank()) {
            VerificationPortfolio override = Portfolios.INSTANCE.byIdOrNull(portfolioIdOverride);
            if (override != null) {
                return override;
            }
            LOGGER.warn("Unknown per-call portfolio override '{}'; falling back to settings", portfolioIdOverride);
        }
        String id = snapshot.get().portfolio();
        VerificationPortfolio portfolio = Portfolios.INSTANCE.byIdOrNull(id);
        if (portfolio == null) {
            LOGGER.warn("Unknown portfolio '{}', falling back to '{}'", id, DEFAULT_PORTFOLIO_ID);
            return Portfolios.INSTANCE.byIdOrNull(DEFAULT_PORTFOLIO_ID);
        }
        return portfolio;
    }

    public Duration resolveTimeout() {
        return Duration.ofSeconds(snapshot.get().timeoutSeconds());
    }

    /** Max concurrent backends, or {@code null} to let the builder choose its default. */
    public Integer resolveMaxConcurrencyOrNull() {
        int value = snapshot.get().maxConcurrency();
        return value > 0 ? value : null;
    }

    public ExecutionEnvironment resolveExecutionEnvironment() {
        Snapshot s = snapshot.get();
        String image = s.thetaDockerImage() != null ? s.thetaDockerImage() : ThetaXstsExecutorKt.THETA_DEFAULT_IMAGE;
        return switch (s.thetaExecutor()) {
            case "shell" ->
                ExecutionEnvironment.builder()
                        .put(ThetaExecutorKey, ThetaXstsExecutor.Companion::shell)
                        .build();
            case "docker" ->
                ExecutionEnvironment.builder()
                        .put(ThetaExecutorKey, () -> ThetaXstsExecutor.Companion.docker(image))
                        .build();
            default ->
                ExecutionEnvironment.builder()
                        .put(ThetaExecutorKey, () -> ThetaXstsExecutor.Companion.autoDetect(image))
                        .build();
        };
    }

    public ArtifactConfig resolveArtifactConfig() {
        Snapshot s = snapshot.get();
        return switch (s.artifactsPreset()) {
            case "none" -> ArtifactConfig.NONE;
            case "debug" -> ArtifactConfig.DEBUG;
            default -> ArtifactConfig.ALL;
        };
    }

    public Path resolveArtifactOutputDirectory() {
        Snapshot s = snapshot.get();
        return switch (s.artifactsLocation()) {
            case "directory" ->
                s.artifactsDirectory() != null && !s.artifactsDirectory().isBlank()
                        ? Path.of(s.artifactsDirectory())
                        : createTempDir();
            default -> createTempDir();
        };
    }

    private static Path createTempDir() {
        try {
            return java.nio.file.Files.createTempDirectory("semantifyr-");
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to create temporary directory for artifacts", e);
        }
    }

    public OptimizationConfig resolveOptimizationConfig() {
        String preset = snapshot.get().optimizationLevel();
        return switch (preset) {
            case "none" -> OptimizationConfig.Companion.getNONE();
            case "all" -> OptimizationConfig.Companion.getALL();
            default -> OptimizationConfig.Companion.getALL();
        };
    }

    private record Snapshot(
            String portfolio,
            long timeoutSeconds,
            int maxConcurrency,
            String thetaExecutor,
            String thetaDockerImage,
            String artifactsLocation,
            String artifactsDirectory,
            String artifactsPreset,
            String optimizationLevel) {
        static Snapshot defaults() {
            return new Snapshot(
                    DEFAULT_PORTFOLIO_ID,
                    DEFAULT_TIMEOUT_SECONDS,
                    0,
                    DEFAULT_THETA_EXECUTOR,
                    ThetaXstsExecutorKt.THETA_DEFAULT_IMAGE,
                    DEFAULT_ARTIFACTS_LOCATION,
                    null,
                    DEFAULT_ARTIFACTS_PRESET,
                    DEFAULT_OPTIMIZATION_LEVEL);
        }

        static Snapshot fromJson(JsonObject o) {
            Snapshot d = defaults();
            return new Snapshot(
                    stringAt(o, "portfolio", d.portfolio()),
                    longAt(o, "timeoutSeconds", d.timeoutSeconds()),
                    intAt(o, "maxConcurrency", d.maxConcurrency()),
                    stringAt(o, "theta.executor", d.thetaExecutor()),
                    stringAt(o, "theta.dockerImage", d.thetaDockerImage()),
                    stringAt(o, "artifacts.location", d.artifactsLocation()),
                    stringAt(o, "artifacts.directory", d.artifactsDirectory()),
                    stringAt(o, "artifacts.preset", d.artifactsPreset()),
                    stringAt(o, "optimization.level", d.optimizationLevel()));
        }

        private static String stringAt(JsonObject o, String path, String fallback) {
            JsonElement el = lookup(o, path);
            if (el instanceof JsonPrimitive p && p.isString()) {
                return p.getAsString();
            }
            return fallback;
        }

        private static long longAt(JsonObject o, String path, long fallback) {
            JsonElement el = lookup(o, path);
            if (el instanceof JsonPrimitive p && p.isNumber()) {
                return p.getAsLong();
            }
            return fallback;
        }

        private static int intAt(JsonObject o, String path, int fallback) {
            JsonElement el = lookup(o, path);
            if (el instanceof JsonPrimitive p && p.isNumber()) {
                return p.getAsInt();
            }
            return fallback;
        }

        private static JsonElement lookup(JsonObject o, String path) {
            if (o.has(path) && !o.get(path).isJsonNull() && !o.get(path).isJsonArray()) {
                return o.get(path);
            }
            String[] parts = path.split("\\.");
            JsonElement current = o;
            for (String part : parts) {
                if (!(current instanceof JsonObject obj)) return null;
                if (!obj.has(part)) return null;
                current = obj.get(part);
            }
            return current;
        }
    }
}
