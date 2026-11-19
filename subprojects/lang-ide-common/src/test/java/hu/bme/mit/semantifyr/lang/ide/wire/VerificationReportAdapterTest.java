/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.wire;

import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.semantifyr.backend.VerificationVerdict;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandGson;
import hu.bme.mit.semantifyr.verifier.ObservedVariable;
import hu.bme.mit.semantifyr.verifier.ReportInitialEntry;
import hu.bme.mit.semantifyr.verifier.ReportKind;
import hu.bme.mit.semantifyr.verifier.ReportState;
import hu.bme.mit.semantifyr.verifier.ReportTrace;
import hu.bme.mit.semantifyr.verifier.ReportType;
import hu.bme.mit.semantifyr.verifier.ReportTypeKind;
import hu.bme.mit.semantifyr.verifier.ReportValue;
import hu.bme.mit.semantifyr.verifier.TestFakesKt;
import hu.bme.mit.semantifyr.verifier.VerificationMetrics;
import hu.bme.mit.semantifyr.verifier.VerificationReport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VerificationReportAdapterTest {

    @Test
    void aNullValueInAStateCrossesTheWire() {
        var trace = new ReportTrace(
                "root",
                List.of(),
                List.of(new ObservedVariable(
                        "root.lamp.partner", new ReportType(ReportTypeKind.Class, "example::Lamp"), null)),
                new ReportInitialEntry(0, List.of()),
                List.of(new ReportState(Map.of("root.lamp.partner", ReportValue.Null.INSTANCE))),
                List.of(),
                null);
        var report = TestFakesKt.fakeVerificationReport(
                VerificationVerdict.Failed, "theta", ReportKind.Run, new VerificationMetrics(), trace);

        var read = CommandGson.INSTANCE.fromJson(CommandGson.INSTANCE.toJson(report), VerificationReport.class);

        assertThat(read).isEqualTo(report);
    }
}
