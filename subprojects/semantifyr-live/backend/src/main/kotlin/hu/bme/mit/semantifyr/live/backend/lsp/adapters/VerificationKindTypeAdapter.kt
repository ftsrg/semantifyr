/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.adapters

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import hu.bme.mit.semantifyr.live.backend.data.VerificationKind

class VerificationKindTypeAdapter : TypeAdapter<VerificationKind>() {

    override fun write(out: JsonWriter, value: VerificationKind?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value.name)
        }
    }

    override fun read(reader: JsonReader): VerificationKind? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        return VerificationKind.valueOf(reader.nextString())
    }

}
