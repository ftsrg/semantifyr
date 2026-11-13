/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment;
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig;
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig;
import hu.bme.mit.semantifyr.lang.ide.server.wire.ArtifactsLocation;
import hu.bme.mit.semantifyr.lang.ide.server.wire.ServerSettingsPayload;
import hu.bme.mit.semantifyr.lang.ide.server.wire.ThetaExecutor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ServerSettingsTest {

    @Test
    void defaultsAreApplied() {
        ServerSettings settings = new ServerSettings();
        assertThat(settings.resolveTimeout()).isEqualTo(Duration.ofSeconds(300));
        assertThat(settings.resolvePortfolio(null).getId()).isEqualTo("smart");
        assertThat(settings.resolveArtifactConfig()).isEqualTo(ArtifactConfig.ALL);
        assertThat(settings.resolveOptimizationConfig()).isSameAs(OptimizationConfig.Companion.getALL());
    }

    @Test
    void aNestedJsonPayloadOverridesTheSpecifiedFields() {
        ServerSettings settings = new ServerSettings();
        settings.apply(JsonParser.parseString("""
                {
                  "portfolio": "theta",
                  "timeoutSeconds": 42,
                  "theta": {"executor": "docker", "dockerImage": "example:1"},
                  "artifacts": {"location": "workspace", "preset": "debug"},
                  "optimization": {"level": "none"}
                }
                """));
        assertThat(settings.resolveTimeout()).isEqualTo(Duration.ofSeconds(42));
        assertThat(settings.resolvePortfolio(null).getId()).isEqualTo("theta");
        assertThat(settings.resolveArtifactConfig()).isEqualTo(ArtifactConfig.DEBUG);
        assertThat(settings.resolveOptimizationConfig()).isSameAs(OptimizationConfig.Companion.getNONE());
    }

    @Test
    void unspecifiedFieldsKeepTheirValue() {
        ServerSettings settings = new ServerSettings();
        settings.apply(JsonParser.parseString("{\"timeoutSeconds\": 7}"));
        assertThat(settings.resolveTimeout()).isEqualTo(Duration.ofSeconds(7));
        // not part of the partial update, so still the default
        assertThat(settings.resolvePortfolio(null).getId()).isEqualTo("smart");
    }

    @Test
    void theSemantifyrWrapperIsUnwrapped() {
        ServerSettings settings = new ServerSettings();
        settings.apply(JsonParser.parseString("{\"semantifyr\": {\"timeoutSeconds\": 11}}"));
        assertThat(settings.resolveTimeout()).isEqualTo(Duration.ofSeconds(11));
    }

    @Test
    void anUnknownArtifactsLocationDoesNotFailAndOtherFieldsStillApply() {
        ServerSettings settings = new ServerSettings();
        assertThatCode(() -> settings.apply(
                        JsonParser.parseString("{\"artifacts\": {\"location\": \"bogus\", \"preset\": \"none\"}}")))
                .doesNotThrowAnyException();
        assertThat(settings.resolveArtifactConfig()).isEqualTo(ArtifactConfig.NONE);
    }

    @Test
    void aNonObjectPayloadIsIgnored() {
        ServerSettings settings = new ServerSettings();
        settings.apply(JsonParser.parseString("42"));
        assertThat(settings.resolveTimeout()).isEqualTo(Duration.ofSeconds(300));
    }

    @Test
    void aNullOrEmptyPayloadIsIgnored() {
        ServerSettings settings = new ServerSettings();
        settings.apply((JsonElement) null);
        settings.apply(JsonParser.parseString("null"));
        settings.apply(JsonParser.parseString("{}"));
        assertThat(settings.resolveTimeout()).isEqualTo(Duration.ofSeconds(300));
    }

    @Test
    void aTypedPayloadAppliesDirectly() {
        ServerSettings settings = new ServerSettings();
        settings.apply(new ServerSettingsPayload("theta", 99L, null, null, null));
        assertThat(settings.resolveTimeout()).isEqualTo(Duration.ofSeconds(99));
        assertThat(settings.resolvePortfolio(null).getId()).isEqualTo("theta");
        // fields the payload leaves null fall back to the built-in defaults
        assertThat(settings.resolveArtifactConfig()).isEqualTo(ArtifactConfig.ALL);
    }

    @Test
    void applyingAPayloadReplacesAllSettings() {
        ServerSettings settings = new ServerSettings();
        settings.apply(new ServerSettingsPayload("theta", 99L, null, null, null));
        settings.apply(ServerSettingsPayload.withArtifactsLocation(ArtifactsLocation.WORKSPACE));
        // the second apply omits the portfolio / timeout, so they are back to the defaults
        assertThat(settings.resolvePortfolio(null).getId()).isEqualTo("smart");
        assertThat(settings.resolveTimeout()).isEqualTo(Duration.ofSeconds(300));
    }

    @Test
    void unchangedSettingsShareOneExecutionEnvironment() {
        ServerSettings settings = new ServerSettings();
        ExecutionEnvironment environment = settings.resolveExecutionEnvironment();

        settings.apply(new ServerSettingsPayload("theta", 99L, null, null, null));

        assertThat(settings.resolveExecutionEnvironment()).isSameAs(environment);
    }

    @Test
    void changedThetaSettingsBuildANewExecutionEnvironment() {
        ServerSettings settings = new ServerSettings();
        ExecutionEnvironment environment = settings.resolveExecutionEnvironment();

        settings.apply(new ServerSettingsPayload(
                null, null, new ServerSettingsPayload.Theta(ThetaExecutor.DOCKER, "example:1"), null, null));
        ExecutionEnvironment changed = settings.resolveExecutionEnvironment();

        assertThat(changed).isNotSameAs(environment);
        assertThat(settings.resolveExecutionEnvironment()).isSameAs(changed);
    }

    @Test
    void temporaryArtifactsShareOneDirectoryPerServer() {
        ServerSettings settings = new ServerSettings();

        Path first = settings.resolveArtifactOutputDirectory(null);
        Path second = settings.resolveArtifactOutputDirectory(null);

        assertThat(first).isEqualTo(second);
        assertThat(Files.isDirectory(first)).isTrue();
        assertThat(new ServerSettings().resolveArtifactOutputDirectory(null)).isNotEqualTo(first);
    }
}
