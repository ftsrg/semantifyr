/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandGson;
import hu.bme.mit.semantifyr.lang.ide.wire.VerificationReportAdapter;
import org.junit.jupiter.api.Test;

class WitnessValidationResultTest {

    private static final String RECHECK_REPORT = """
            {
                "version": 2,
                "kind": "re-check",
                "verificationCase": "example::LampCase",
                "portfolioId": "smart",
                "optimization": {
                    "enabled": []
                },
                "timeout": "PT5M",
                "result": {
                    "metadata": {
                        "backendId": "spin:safe-dfs",
                        "startedAt": "2026-11-16T10:00:00Z"
                    },
                    "verdict": "Passed",
                    "soundness": "UnderApproximating",
                    "metrics": {
                        "totalDuration": "PT1.5S"
                    }
                },
                "trace": null
            }
            """;

    @Test
    void theValidationResultCarriesTheRecheckReportAsItsFileHoldsIt() {
        var report = VerificationReportAdapter.decode(RECHECK_REPORT);
        var validation = hu.bme.mit.semantifyr.verifier.witness.WitnessValidationResult.from(report.getResult());
        var result = WitnessValidationResult.fromValidation(validation, report, "smart");

        var wire = CommandGson.INSTANCE.toJsonTree(result).getAsJsonObject();

        var file = JsonParser.parseString(RECHECK_REPORT).getAsJsonObject();
        assertThat(wire.getAsJsonObject("report").get("kind")).isEqualTo(file.get("kind"));
        assertThat(wire.getAsJsonObject("report").getAsJsonObject("result").get("soundness"))
                .isEqualTo(file.getAsJsonObject("result").get("soundness"));
        assertThat(wire.getAsJsonObject("report").get("trace").isJsonNull()).isTrue();
        assertThat(wire.get("status").getAsString()).isEqualTo(WitnessValidationStatus.VALID);
        assertThat(CommandGson.INSTANCE
                        .fromJson(wire, WitnessValidationResult.class)
                        .report())
                .isEqualTo(report);
    }
}
