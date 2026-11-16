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

class VerificationCaseResultTest {

    private static final String REPORT = """
            {
                "version": 2,
                "kind": "run",
                "verificationCase": "example::LampCase",
                "portfolioId": "smart",
                "optimization": {
                    "enabled": []
                },
                "timeout": "PT5M",
                "result": {
                    "metadata": {
                        "backendId": "theta:cegar-expl",
                        "startedAt": "2026-11-15T10:00:00Z"
                    },
                    "verdict": "Failed",
                    "metrics": {}
                },
                "trace": {
                    "root": "root",
                    "transitions": [
                        {
                            "transition": "example::Lamp::toggle",
                            "parameters": [
                                {
                                    "parameter": "partner",
                                    "type": {
                                        "kind": "class",
                                        "name": "example::Lamp",
                                        "element": null,
                                        "size": null
                                    }
                                }
                            ]
                        }
                    ],
                    "variables": [
                        {
                            "variable": "root.lamp.partner",
                            "type": {
                                "kind": "class",
                                "name": "example::Lamp",
                                "element": null,
                                "size": null
                            },
                            "slot": null
                        },
                        {
                            "variable": "root.lamp.count",
                            "type": {
                                "kind": "datatype",
                                "name": "semantifyr::int",
                                "element": null,
                                "size": null
                            },
                            "slot": null
                        }
                    ],
                    "initial": {
                        "state": 0,
                        "calls": []
                    },
                    "states": [
                        {
                            "values": {
                                "root.lamp.partner": null,
                                "root.lamp.count": "9007199254740993"
                            }
                        },
                        {
                            "values": {
                                "root.lamp.partner": "root.hall",
                                "root.lamp.count": 1
                            }
                        }
                    ],
                    "steps": [
                        {
                            "source": 0,
                            "target": 1,
                            "elapsed": "3/2",
                            "calls": [
                                {
                                    "self": "root.lamp",
                                    "transition": "example::Lamp::toggle",
                                    "arguments": {
                                        "partner": "root.hall"
                                    },
                                    "calls": []
                                }
                            ]
                        }
                    ],
                    "missingCalls": null
                }
            }
            """;

    @Test
    void theWireCarriesTheReportAsItsFileHoldsIt() {
        var report = VerificationReportAdapter.decode(REPORT);
        var result = VerificationCaseResult.fromReport(report, "smart", "file:/case/witness.semantifyr");

        var wire = CommandGson.INSTANCE.toJsonTree(result).getAsJsonObject();

        var file = JsonParser.parseString(REPORT).getAsJsonObject();
        assertThat(wire.getAsJsonObject("report").get("trace")).isEqualTo(file.get("trace"));
        assertThat(wire.getAsJsonObject("report").get("kind")).isEqualTo(file.get("kind"));
        assertThat(wire.get("status").getAsString()).isEqualTo(VerificationStatus.FAILED);
        assertThat(CommandGson.INSTANCE
                        .fromJson(wire, VerificationCaseResult.class)
                        .report())
                .isEqualTo(report);
    }
}
