/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.adapters

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import kotlin.time.Duration

class DurationGsonAdapter : TypeAdapter<Duration>() {

    override fun write(out: JsonWriter, value: Duration?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value.toIsoString())
        }
    }

    override fun read(input: JsonReader): Duration {
        return Duration.parseIsoString(input.nextString())
    }
}
