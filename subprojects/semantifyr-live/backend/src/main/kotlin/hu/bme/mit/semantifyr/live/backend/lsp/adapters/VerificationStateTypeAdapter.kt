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
import hu.bme.mit.semantifyr.live.backend.data.VerificationState

class VerificationStateTypeAdapter : TypeAdapter<VerificationState>() {

    override fun write(out: JsonWriter, value: VerificationState?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value.name)
        }
    }

    override fun read(reader: JsonReader): VerificationState? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        return VerificationState.valueOf(reader.nextString())
    }
}
