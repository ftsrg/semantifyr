/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.wire;

public record ServerSettingsPayload(
        String portfolio,
        Long timeoutSeconds,
        Integer maxConcurrency,
        Theta theta,
        Artifacts artifacts,
        Optimization optimization) {

    public record Theta(ThetaExecutor executor, String dockerImage) {}

    public record Artifacts(ArtifactsLocation location, ArtifactsPreset preset) {}

    public record Optimization(OptimizationLevel level) {}

    public static ServerSettingsPayload withArtifactsLocation(ArtifactsLocation location) {
        return new ServerSettingsPayload(null, null, null, null, new Artifacts(location, null), null);
    }
}
