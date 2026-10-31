/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.wire;

import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.semantifyr.compiler.pipeline.context.ModelSize;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandGson;
import hu.bme.mit.semantifyr.verifier.AnalysisStatsDto;
import hu.bme.mit.semantifyr.verifier.CompilationMetricsDto;
import hu.bme.mit.semantifyr.verifier.OptimizationStatsDto;
import hu.bme.mit.semantifyr.verifier.PassStatsDto;
import hu.bme.mit.semantifyr.verifier.VerificationMetricsDto;
import hu.bme.mit.semantifyr.verifier.VerifierMetricsDto;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerificationMetricsTest {

    @Test
    void theWireMetricsCarryTheOptimizerStatsAndModelSizes() {
        var optimization = new OptimizationStatsDto(
                Duration.ofMillis(5),
                List.of(new PassStatsDto("CopyPropagationPass", Duration.ofMillis(3), 2, 1)),
                List.of(new AnalysisStatsDto("ReachingDefinitionsAnalysis", Duration.ofMillis(1), 2)));
        var compilation = new CompilationMetricsDto(
                Duration.ofMillis(1),
                Duration.ofMillis(2),
                Duration.ofMillis(3),
                Duration.ofMillis(4),
                optimization,
                optimization,
                new ModelSize(3, 10, 20),
                new ModelSize(2, 8, 15),
                new ModelSize(1, 5, 9));
        var verifier = new VerifierMetricsDto(Duration.ofMillis(10), compilation, Duration.ZERO, Duration.ofMillis(7));
        var metrics = VerificationMetrics.fromDto(new VerificationMetricsDto(Duration.ofMillis(20), null, verifier));

        var json = CommandGson.INSTANCE.toJsonTree(metrics).getAsJsonObject().getAsJsonObject("compilation");

        assertThat(json.getAsJsonObject("flattenedSize").get("variables").getAsInt())
                .isEqualTo(1);
        var pass = json.getAsJsonObject("inliningOptimization")
                .getAsJsonArray("passes")
                .get(0)
                .getAsJsonObject();
        assertThat(pass.get("firings").getAsInt()).isEqualTo(1);
        var analysis = json.getAsJsonObject("flatteningOptimization")
                .getAsJsonArray("analyses")
                .get(0)
                .getAsJsonObject();
        assertThat(analysis.get("duration").getAsString()).isEqualTo("PT0.001S");
    }
}
