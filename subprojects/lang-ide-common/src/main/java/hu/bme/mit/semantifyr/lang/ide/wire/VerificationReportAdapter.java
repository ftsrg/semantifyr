/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.wire;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import hu.bme.mit.semantifyr.verifier.VerificationReport;
import java.io.IOException;
import kotlin.Unit;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonKt;

public final class VerificationReportAdapter extends TypeAdapter<VerificationReport> {

    private static final Json JSON = JsonKt.Json(Json.Default, builder -> {
        builder.setEncodeDefaults(true);
        return Unit.INSTANCE;
    });

    private static final TypeAdapter<JsonElement> ELEMENT = new Gson().getAdapter(JsonElement.class);

    @Override
    public void write(JsonWriter out, VerificationReport report) throws IOException {
        if (report == null) {
            out.nullValue();
            return;
        }
        out.jsonValue(JSON.encodeToString(VerificationReport.Companion.serializer(), report));
    }

    @Override
    public VerificationReport read(JsonReader in) throws IOException {
        var element = ELEMENT.read(in);
        if (element.isJsonNull()) {
            return null;
        }
        return JSON.decodeFromString(VerificationReport.Companion.serializer(), element.toString());
    }
}
