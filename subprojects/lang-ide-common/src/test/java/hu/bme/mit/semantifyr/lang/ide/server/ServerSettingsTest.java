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
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig;
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig;
import hu.bme.mit.semantifyr.lang.ide.server.wire.ArtifactsLocation;
import hu.bme.mit.semantifyr.lang.ide.server.wire.ServerSettingsPayload;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ServerSettingsTest {

    @Test
    void defaultsAreApplied() {
        ServerSettings settings = new ServerSettings();
        assertThat(settings.resolveTimeout()).isEqualTo(Duration.ofSeconds(300));
        assertThat(settings.resolveMaxConcurrencyOrNull()).isNull();
        assertThat(settings.resolvePortfolio(null).getId()).isEqualTo("theta-full");
        assertThat(settings.resolveArtifactConfig()).isEqualTo(ArtifactConfig.ALL);
        assertThat(settings.resolveOptimizationConfig()).isSameAs(OptimizationConfig.Companion.getALL());
    }

    @Test
    void aNestedJsonPayloadOverridesTheSpecifiedFields() {
        ServerSettings settings = new ServerSettings();
        settings.apply(JsonParser.parseString("""
                {
                  "portfolio": "smart-full",
                  "timeoutSeconds": 42,
                  "maxConcurrency": 3,
                  "theta": {"executor": "docker", "dockerImage": "example:1"},
                  "artifacts": {"location": "workspace", "preset": "debug"},
                  "optimization": {"level": "none"}
                }
                """));
        assertThat(settings.resolveTimeout()).isEqualTo(Duration.ofSeconds(42));
        assertThat(settings.resolveMaxConcurrencyOrNull()).isEqualTo(3);
        assertThat(settings.resolvePortfolio(null).getId()).isEqualTo("smart-full");
        assertThat(settings.resolveArtifactConfig()).isEqualTo(ArtifactConfig.DEBUG);
        assertThat(settings.resolveOptimizationConfig()).isSameAs(OptimizationConfig.Companion.getNONE());
    }

    @Test
    void unspecifiedFieldsKeepTheirValue() {
        ServerSettings settings = new ServerSettings();
        settings.apply(JsonParser.parseString("{\"timeoutSeconds\": 7}"));
        assertThat(settings.resolveTimeout()).isEqualTo(Duration.ofSeconds(7));
        // not part of the partial update, so still the default
        assertThat(settings.resolvePortfolio(null).getId()).isEqualTo("theta-full");
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
        settings.apply(new ServerSettingsPayload("smart-full", 99L, null, null, null, null));
        assertThat(settings.resolveTimeout()).isEqualTo(Duration.ofSeconds(99));
        assertThat(settings.resolvePortfolio(null).getId()).isEqualTo("smart-full");
        // fields the payload leaves null fall back to the built-in defaults
        assertThat(settings.resolveMaxConcurrencyOrNull()).isNull();
    }

    @Test
    void applyingAPayloadReplacesAllSettings() {
        ServerSettings settings = new ServerSettings();
        settings.apply(new ServerSettingsPayload("smart-full", 99L, null, null, null, null));
        settings.apply(ServerSettingsPayload.withArtifactsLocation(ArtifactsLocation.WORKSPACE));
        // the second apply omits the portfolio / timeout, so they are back to the defaults
        assertThat(settings.resolvePortfolio(null).getId()).isEqualTo("theta-full");
        assertThat(settings.resolveTimeout()).isEqualTo(Duration.ofSeconds(300));
    }
}
