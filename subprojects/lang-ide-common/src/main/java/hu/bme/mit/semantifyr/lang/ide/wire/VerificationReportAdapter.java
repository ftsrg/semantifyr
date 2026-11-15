/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.wire;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import hu.bme.mit.semantifyr.verifier.VerificationReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import kotlin.Unit;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonKt;

// the wire carries the report in the serialization of its file, nulls included, never in Gson's reading of its types
public final class VerificationReportAdapter extends TypeAdapter<VerificationReport> {

    private static final Json JSON = JsonKt.Json(Json.Default, builder -> {
        builder.setEncodeDefaults(true);
        return Unit.INSTANCE;
    });

    private static final TypeAdapter<JsonElement> ELEMENT = new Gson().getAdapter(JsonElement.class);

    public static VerificationReport read(Path reportPath) throws IOException {
        return decode(Files.readString(reportPath));
    }

    public static VerificationReport decode(String text) {
        return JSON.decodeFromString(VerificationReport.Companion.serializer(), text);
    }

    public static String encode(VerificationReport report) {
        return JSON.encodeToString(VerificationReport.Companion.serializer(), report);
    }

    @Override
    public void write(JsonWriter out, VerificationReport report) throws IOException {
        if (report == null) {
            out.nullValue();
            return;
        }
        var serializeNulls = out.getSerializeNulls();
        out.setSerializeNulls(true);
        try {
            ELEMENT.write(out, JsonParser.parseString(encode(report)));
        } finally {
            out.setSerializeNulls(serializeNulls);
        }
    }

    @Override
    public VerificationReport read(JsonReader in) throws IOException {
        var element = ELEMENT.read(in);
        if (element.isJsonNull()) {
            return null;
        }
        return decode(element.toString());
    }
}
