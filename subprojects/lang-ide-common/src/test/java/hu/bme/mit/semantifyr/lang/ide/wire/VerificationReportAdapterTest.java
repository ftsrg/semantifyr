/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.wire;

import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.semantifyr.backend.VerificationVerdict;
import hu.bme.mit.semantifyr.backend.witness.Fraction;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandGson;
import hu.bme.mit.semantifyr.lang.ide.server.wire.VerificationCaseResult;
import hu.bme.mit.semantifyr.verifier.ObservedVariable;
import hu.bme.mit.semantifyr.verifier.ReportInitialEntry;
import hu.bme.mit.semantifyr.verifier.ReportKind;
import hu.bme.mit.semantifyr.verifier.ReportState;
import hu.bme.mit.semantifyr.verifier.ReportStep;
import hu.bme.mit.semantifyr.verifier.ReportTrace;
import hu.bme.mit.semantifyr.verifier.ReportType;
import hu.bme.mit.semantifyr.verifier.ReportTypeKind;
import hu.bme.mit.semantifyr.verifier.ReportValue;
import hu.bme.mit.semantifyr.verifier.TestFakesKt;
import hu.bme.mit.semantifyr.verifier.VerificationMetrics;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VerificationReportAdapterTest {

    @Test
    void aReportBuiltInCodeCrossesTheWireUnchanged() {
        var trace = new ReportTrace(
                "root",
                List.of(),
                List.of(new ObservedVariable(
                        "root.lamp.on", new ReportType(ReportTypeKind.Datatype, "semantifyr::bool", null, null), null)),
                new ReportInitialEntry(0, List.of()),
                List.of(
                        new ReportState(Map.of(
                                "root.lamp.on",
                                new ReportValue.Bool(false),
                                "root.lamp.partner",
                                ReportValue.Null.INSTANCE)),
                        new ReportState(Map.of(
                                "root.lamp.on", new ReportValue.Bool(true),
                                "root.count", new ReportValue.Integer(BigInteger.TWO)))),
                List.of(new ReportStep(0, 1, Fraction.Companion.of(3, 2), List.of())),
                null);
        var report = TestFakesKt.fakeVerificationReport(
                VerificationVerdict.Failed, "theta", ReportKind.Run, new VerificationMetrics(), trace);
        var result = VerificationCaseResult.fromReport(report, "smart", null);

        var wire = CommandGson.INSTANCE.toJsonTree(result).getAsJsonObject();

        assertThat(wire.getAsJsonObject("report")
                        .getAsJsonObject("trace")
                        .getAsJsonArray("states")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonObject("values")
                        .get("root.lamp.partner")
                        .isJsonNull())
                .isTrue();
        assertThat(CommandGson.INSTANCE
                        .fromJson(wire, VerificationCaseResult.class)
                        .report())
                .isEqualTo(report);
    }
}
