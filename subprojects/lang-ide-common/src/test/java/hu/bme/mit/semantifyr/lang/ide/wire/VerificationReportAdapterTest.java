/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
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
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.junit.jupiter.api.Test;

class VerificationReportAdapterTest {

    @Test
    void aReportCrossesTheLanguageServersWireAsWritten() {
        var trace = new ReportTrace(
                "root",
                List.of(),
                List.of(new ObservedVariable(
                        "root.lamp.partner", new ReportType(ReportTypeKind.Class, "example::Lamp"), null)),
                new ReportInitialEntry(0, List.of()),
                List.of(new ReportState(Map.of("root.lamp.partner", ReportValue.Null.INSTANCE))),
                List.of());
        var report = TestFakesKt.fakeVerificationReport(
                VerificationVerdict.Failed, "theta", ReportKind.Run, new VerificationMetrics(), trace);

        var handler = new MessageJsonHandler(Map.of(), CommandGson::configure);
        var response = new ResponseMessage();
        response.setId(1);
        response.setResult(report);
        var written = new StringWriter();
        handler.serialize(response, written);
        var result =
                JsonParser.parseString(written.toString()).getAsJsonObject().get("result");

        var read = CommandGson.INSTANCE.fromJson(result, VerificationReport.class);

        assertThat(read).isEqualTo(report);
    }
}
